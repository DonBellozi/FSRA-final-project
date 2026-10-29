package ru.mephi.fsra.booking.controller;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.mephi.fsra.booking.client.HotelClient;
import ru.mephi.fsra.booking.dto.HotelRecommendation;
import ru.mephi.fsra.booking.dto.RoomResponse;
import ru.mephi.fsra.booking.entity.Booking;
import ru.mephi.fsra.booking.service.BookingService;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

  private final BookingService bookingService;
  private final HotelClient hotelClient;

  public BookingController(BookingService bookingService, HotelClient hotelClient) {
    this.bookingService = bookingService;
    this.hotelClient = hotelClient;
  }

  // Получение userId из проверенного JWT
  private Long getCurrentUserId(JwtAuthenticationToken authentication) {
    Number userId = authentication.getToken().getClaim("userId");

    if (userId == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT does not contain userId");
    }

    return userId.longValue();
  }

  // Проверка доступа к данным пользователя
  private void checkUserAccess(Long userId, JwtAuthenticationToken authentication) {
    Long currentUserId = getCurrentUserId(authentication);

    String role = authentication.getToken().getClaimAsString("role");

    if (!"ADMIN".equals(role) && !userId.equals(currentUserId)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Access to another user's data is forbidden");
    }
  }

  // Создание бронирования
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Booking createBooking(
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Booking booking,
      JwtAuthenticationToken authentication) {
    Long currentUserId = getCurrentUserId(authentication);

    // Не доверяем userId, переданному клиентом
    booking.setUserId(currentUserId);

    return bookingService.createBooking(booking, key);
  }

  // Получение всех бронирований
  // Доступ ADMIN ограничен в SecurityConfig
  @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
  @GetMapping
  public Object getAllBookings(
      @RequestParam(value = "page", required = false) Integer page,
      @RequestParam(value = "size", required = false) Integer size) {
    return page == null && size == null
        ? bookingService.getAllBookings()
        : bookingService.getBookingPage(null, page, size);
  }

  // Получение бронирования по ID
  @GetMapping("/{id}")
  public Booking getBookingById(
      @PathVariable("id") Long id, JwtAuthenticationToken authentication) {
    Booking booking = bookingService.getBookingById(id);

    checkUserAccess(booking.getUserId(), authentication);

    return booking;
  }

  // Проверка взаимодействия с hotel-service
  @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/test-room/{roomId}")
  public RoomResponse testRoom(@PathVariable("roomId") Long roomId) {
    return hotelClient.getRoomById(roomId);
  }

  // Бронирования конкретного пользователя
  @GetMapping("/user/{userId}")
  public Object getBookingsByUserId(
      @PathVariable("userId") Long userId,
      JwtAuthenticationToken authentication,
      @RequestParam(value = "page", required = false) Integer page,
      @RequestParam(value = "size", required = false) Integer size) {
    checkUserAccess(userId, authentication);
    return page == null && size == null
        ? bookingService.getBookingsByUserId(userId)
        : bookingService.getBookingPage(userId, page, size);
  }

  // Статистика бронирований
  // Доступ ADMIN ограничен в SecurityConfig
  @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/stats")
  public Map<String, Long> getBookingStats() {
    return bookingService.getBookingStats();
  }

  // Рекомендации отелей
  @GetMapping("/recommendations/{userId}")
  public List<HotelRecommendation> getHotelRecommendations(
      @PathVariable("userId") Long userId, JwtAuthenticationToken authentication) {
    checkUserAccess(userId, authentication);

    return bookingService.getHotelRecommendations(userId);
  }

  // Отмена бронирования
  @PatchMapping("/{id}/cancel")
  public Booking cancelBooking(@PathVariable("id") Long id, JwtAuthenticationToken authentication) {
    Booking booking = bookingService.getBookingById(id);

    checkUserAccess(booking.getUserId(), authentication);

    return bookingService.cancelBooking(id);
  }
}
