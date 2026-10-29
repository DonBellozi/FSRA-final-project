
param(
    [string]$BookingUrl = "http://localhost:8082",
    [string]$HotelUrl = "http://localhost:8081",
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$AdminUsername = $(if ($env:FSRA_ADMIN_USERNAME) { $env:FSRA_ADMIN_USERNAME } else { "admin" }),
    [string]$AdminPassword = $(if ($env:FSRA_ADMIN_PASSWORD) { $env:FSRA_ADMIN_PASSWORD } else { "StudyAdmin123!" })
)

$ErrorActionPreference = "Stop"
if (-not $AdminUsername -or -not $AdminPassword) {
    throw "Admin username and password must not be empty."
}
$loginBody = @{ username = $AdminUsername; password = $AdminPassword } | ConvertTo-Json
$login = Invoke-RestMethod -Uri "$GatewayUrl/api/auth/login" -Method POST -ContentType "application/json" -Body $loginBody
$script:AdminHeaders = @{ Authorization = "Bearer $($login.accessToken)" }

function Invoke-ApiPost {
    param(
        [string]$Url,
        [hashtable]$Data
    )

    $json = $Data | ConvertTo-Json -Depth 5

    Invoke-RestMethod `
        -Uri $Url `
        -Headers $script:AdminHeaders `
        -Method POST `
        -ContentType "application/json; charset=utf-8" `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($json))
}

Write-Host "`n=== USERS ===" -ForegroundColor Cyan

for ($i = 1; $i -le 3; $i++) {
    $username = "user$i"

    try {
        $user = Invoke-ApiPost `
            -Url "$BookingUrl/api/users/register" `
            -Data @{
                username = $username
                password = "TestUser${i}123!"
            }

        Write-Host "CREATED USER: $($user.username) ID=$($user.id)" `
            -ForegroundColor Green
    }
    catch {
        if ([int]$_.Exception.Response.StatusCode -eq 409) {
            Write-Host "EXISTS USER: $username" -ForegroundColor Yellow
        }
        else {
            throw
        }
    }
}

Write-Host "`n=== HOTELS ===" -ForegroundColor Cyan

$hotels = @(
    @{
        name = "Radisson Blu Olympiyskiy Hotel, Moscow"
        address = "Москва, Самарская улица, 1"
        rooms = @("501", "502", "503")
    },
    @{
        name = "Cosmos Moscow VDNH Hotel"
        address = "Москва, проспект Мира, 150"
        rooms = @("601", "602", "603")
    },
    @{
        name = "Hotel Metropol Moscow"
        address = "Москва, Театральный проезд, 2"
        rooms = @("701", "702", "703")
    }
)

$existingHotels = Invoke-RestMethod -Uri "$HotelUrl/api/hotels" -Headers $script:AdminHeaders -Method GET

foreach ($hotelData in $hotels) {

    $existing = $existingHotels |
        Where-Object { $_.name -eq $hotelData.name } |
        Select-Object -First 1

    if ($null -ne $existing) {
        $hotel = $existing
        Write-Host "EXISTS HOTEL: $($hotel.name) ID=$($hotel.id)" `
            -ForegroundColor Yellow
    }
    else {
        $hotel = Invoke-ApiPost `
            -Url "$HotelUrl/api/hotels" `
            -Data @{
                name = $hotelData.name
                address = $hotelData.address
            }

        Write-Host "CREATED HOTEL: $($hotel.name) ID=$($hotel.id)" `
            -ForegroundColor Green
    }

    $existingRooms = Invoke-RestMethod `
            -Uri "$HotelUrl/api/rooms/hotel/$($hotel.id)" `
            -Headers $script:AdminHeaders `
            -Method GET

    foreach ($roomNumber in $hotelData.rooms) {

        $existingRoom = $existingRooms |
            Where-Object { $_.number -eq $roomNumber } |
            Select-Object -First 1

        if ($null -ne $existingRoom) {
            Write-Host "  EXISTS ROOM: $roomNumber" -ForegroundColor Yellow
            continue
        }

        $room = Invoke-ApiPost `
            -Url "$HotelUrl/api/rooms/hotel/$($hotel.id)" `
            -Data @{
                hotelId = $hotel.id
                number = $roomNumber
                available = $true
            }

        Write-Host "  CREATED ROOM: $roomNumber ID=$($room.id)" `
            -ForegroundColor Green
    }
}

Write-Host "`n=== DEMONSTRATION BOOKINGS ===" -ForegroundColor Cyan
$catalog = Invoke-RestMethod -Uri "$HotelUrl/api/hotels" -Headers $script:AdminHeaders
$demoHotel = $catalog | Where-Object { $_.name -eq $hotels[0].name } | Select-Object -First 1
$demoRoomsResponse = Invoke-RestMethod -Uri "$HotelUrl/api/rooms/hotel/$($demoHotel.id)" -Headers $script:AdminHeaders
$demoRooms = @($demoRoomsResponse | Sort-Object id)
if ($demoRooms.Count -lt 3) { throw "At least three demo rooms required" }
# Fixed dates and keys make repeated initialization reproducible.
$scenarios = @(
    @{ key = "demo-room-a-first-v1"; user = 1; room = 0; start = "2027-06-01"; end = "2027-06-03"; cancel = $false },
    @{ key = "demo-room-a-second-v1"; user = 1; room = 0; start = "2027-06-10"; end = "2027-06-12"; cancel = $false },
    @{ key = "demo-room-b-v1"; user = 2; room = 1; start = "2027-06-01"; end = "2027-06-04"; cancel = $false },
    @{ key = "demo-cancelled-v1"; user = 3; room = 2; start = "2027-06-01"; end = "2027-06-03"; cancel = $true }
)
foreach ($scenario in $scenarios) {
    $userIndex = $scenario.user
    $credentials = @{ username = "user$userIndex"; password = "TestUser${userIndex}123!" } | ConvertTo-Json
    $auth = Invoke-RestMethod -Uri "$GatewayUrl/api/users/auth" -Method POST -ContentType "application/json" -Body $credentials
    $bookingHeaders = @{ Authorization = "Bearer $($auth.accessToken)"; 'Idempotency-Key' = $scenario.key }
    $bookingBody = @{ roomId = $demoRooms[$scenario.room].id; autoSelect = $false; checkIn = $scenario.start; checkOut = $scenario.end } | ConvertTo-Json
    $booking = Invoke-RestMethod -Uri "$GatewayUrl/api/bookings" -Method POST -Headers $bookingHeaders -ContentType "application/json" -Body $bookingBody
    if ($scenario.cancel) {
        $booking = Invoke-RestMethod -Uri "$GatewayUrl/api/bookings/$($booking.id)/cancel" -Method PATCH -Headers $bookingHeaders
    }
    if ((-not $scenario.cancel) -and $booking.status -ne "CONFIRMED") {
        throw "Demo key $($scenario.key) is not CONFIRMED; use clean databases for reproducible demonstration."
    }
    Write-Host "DEMO BOOKING: $($booking.id) ROOM=$($booking.roomId) STATUS=$($booking.status)" -ForegroundColor Green
}

Write-Host "`n=== INITIALIZATION COMPLETE ===" -ForegroundColor Cyan
