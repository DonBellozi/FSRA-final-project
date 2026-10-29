package ru.mephi.fsra.booking.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import feign.FeignException;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import ru.mephi.fsra.booking.client.HotelClient;
import ru.mephi.fsra.booking.entity.Booking;
import ru.mephi.fsra.booking.entity.BookingStatus;
import ru.mephi.fsra.booking.repository.BookingRepository;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

  @Mock private BookingRepository bookingRepository;

  @Mock private HotelClient hotelClient;

  @InjectMocks private BookingService bookingService;

  private Booking booking;

  @Test
  void autoSelect_ignoresProvidedRoomId() {
    booking.setAutoSelect(true);
    booking.setRoomId(999L);
    when(bookingRepository.findByIdempotencyKey("auto")).thenReturn(Optional.empty());
    when(hotelClient.recommendRooms(booking.getCheckIn(), booking.getCheckOut()))
        .thenReturn(
            java.util.List.of(
                new ru.mephi.fsra.booking.dto.RoomResponse(12L, "12", true, 0, null)));
    mockSaveAndFlush();
    Booking result = bookingService.createBooking(booking, "auto");
    assertEquals(12L, result.getRoomId());
    verify(hotelClient).lockRoom(argThat(request -> request.roomId().equals(12L)));
  }

  @Test
  void sameKeyCannotSwitchFromManualToAutomaticSelection() {
    Booking existing = new Booking();
    existing.setUserId(1L);
    existing.setRoomId(10L);
    existing.setCheckIn(booking.getCheckIn());
    existing.setCheckOut(booking.getCheckOut());
    when(bookingRepository.findByIdempotencyKey("mode-change")).thenReturn(Optional.of(existing));
    booking.setAutoSelect(true);
    var error =
        assertThrows(
            ResponseStatusException.class,
            () -> bookingService.createBooking(booking, "mode-change"));
    assertEquals(409, error.getStatusCode().value());
    verifyNoInteractions(hotelClient);
  }

  private feign.RetryableException timeout() {
    Request request =
        Request.create(
            Request.HttpMethod.POST,
            "/api/room-locks",
            Map.of(),
            null,
            StandardCharsets.UTF_8,
            null);
    return new feign.RetryableException(
        -1,
        "Read timed out",
        Request.HttpMethod.POST,
        new java.net.SocketTimeoutException("Read timed out"),
        (Long) null,
        request);
  }

  @Test
  void lostResponseRetriesWithSameBookingIdAndConfirms() {
    when(bookingRepository.findByIdempotencyKey("lost-response")).thenReturn(Optional.empty());
    mockSaveAndFlush();
    when(hotelClient.lockRoom(any())).thenThrow(timeout()).thenReturn(null);
    Booking result = bookingService.createBooking(booking, "lost-response");
    assertEquals(BookingStatus.CONFIRMED, result.getStatus());
    var requests =
        org.mockito.ArgumentCaptor.forClass(ru.mephi.fsra.booking.dto.RoomLockRequest.class);
    verify(hotelClient, times(2)).lockRoom(requests.capture());
    assertEquals(requests.getAllValues().get(0), requests.getAllValues().get(1));
    verify(hotelClient, never()).unlockRoom(any(), any());
  }

  @Test
  void exhaustedTimeoutsQueueCompensationWhenReleaseAlsoFails() {
    when(bookingRepository.findByIdempotencyKey("timeout")).thenReturn(Optional.empty());
    mockSaveAndFlush();
    when(hotelClient.lockRoom(any())).thenThrow(timeout());
    doThrow(timeout()).when(hotelClient).unlockRoom(10L, 100L);
    assertEquals(
        503,
        assertThrows(
                ResponseStatusException.class,
                () -> bookingService.createBooking(booking, "timeout"))
            .getStatusCode()
            .value());
    verify(hotelClient, times(3)).lockRoom(any());
    verify(bookingRepository).requestCancellation(100L);
    verify(bookingRepository, never()).acknowledgeCompensation(any());
    assertEquals(BookingStatus.CANCELLED, booking.getStatus());
    assertTrue(booking.isCompensationPending());
  }

  @BeforeEach
  void setUp() {
    booking = new Booking();
    booking.setUserId(1L);
    booking.setRoomId(10L);
    booking.setCheckIn(LocalDate.of(2026, 11, 10));
    booking.setCheckOut(LocalDate.of(2026, 11, 15));
  }

  // Тест 1. Успешное бронирование
  @Test
  void createBooking_shouldConfirmBooking_whenRoomLockSucceeds() {

    String key = "test-key-success";

    when(bookingRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());

    mockSaveAndFlush();

    Booking result = bookingService.createBooking(booking, key);

    assertNotNull(result);
    assertEquals(100L, result.getId());
    assertEquals(BookingStatus.CONFIRMED, result.getStatus());

    verify(hotelClient, times(1)).lockRoom(any());
    verify(bookingRepository, times(1)).saveAndFlush(any(Booking.class));
  }

  // Тест 2. Повторный запрос с тем же ключом
  @Test
  void createBooking_shouldReturnExistingBooking_whenIdempotencyKeyRepeated() {

    String key = "test-key-duplicate";

    Booking existing = new Booking();
    existing.setId(50L);
    existing.setUserId(1L);
    existing.setRoomId(10L);
    existing.setCheckIn(LocalDate.of(2026, 11, 10));
    existing.setCheckOut(LocalDate.of(2026, 11, 15));
    existing.setIdempotencyKey(key);
    existing.setStatus(BookingStatus.CONFIRMED);

    when(bookingRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

    Booking result = bookingService.createBooking(booking, key);

    assertNotNull(result);
    assertEquals(50L, result.getId());
    assertEquals(BookingStatus.CONFIRMED, result.getStatus());

    verify(bookingRepository, never()).saveAndFlush(any(Booking.class));

    verifyNoInteractions(hotelClient);
  }

  // Тест 3. Hotel-service недоступен
  @Test
  void createBooking_shouldCancelAndCompensate_whenHotelServiceUnavailable() {

    String key = "test-key-unavailable";

    when(bookingRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());

    mockSaveAndFlush();

    doThrow(createFeignException(503, "Service Unavailable")).when(hotelClient).lockRoom(any());

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class, () -> bookingService.createBooking(booking, key));

    assertEquals(503, exception.getStatusCode().value());
    assertEquals(100L, booking.getId());
    assertEquals(BookingStatus.CANCELLED, booking.getStatus());

    verify(hotelClient, times(3)).lockRoom(any());
    verify(hotelClient).unlockRoom(10L, 100L);
    verify(bookingRepository, times(1)).saveAndFlush(any(Booking.class));
  }

  // Тест 4. Номер уже занят
  @Test
  void createBooking_shouldCancelBooking_whenRoomAlreadyBooked() {

    String key = "test-key-conflict";

    when(bookingRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());

    mockSaveAndFlush();

    doThrow(createFeignException(409, "Conflict")).when(hotelClient).lockRoom(any());

    ResponseStatusException error =
        assertThrows(
            ResponseStatusException.class, () -> bookingService.createBooking(booking, key));
    assertEquals(409, error.getStatusCode().value());
    assertEquals(BookingStatus.CANCELLED, booking.getStatus());
    verify(hotelClient).unlockRoom(10L, 100L);

    verify(hotelClient, times(1)).lockRoom(any());
    verify(bookingRepository, times(1)).saveAndFlush(any(Booking.class));
  }

  // Вспомогательный метод сохранения бронирования
  private void mockSaveAndFlush() {
    lenient().when(bookingRepository.confirmPending(100L)).thenReturn(1);

    when(bookingRepository.saveAndFlush(any(Booking.class)))
        .thenAnswer(
            invocation -> {
              Booking saved = invocation.getArgument(0);
              saved.setId(100L);
              return saved;
            });
  }

  // Вспомогательный метод создания ошибки Feign
  private FeignException createFeignException(int status, String reason) {

    Request request =
        Request.create(
            Request.HttpMethod.POST,
            "/api/room-locks",
            Map.of(),
            null,
            StandardCharsets.UTF_8,
            null);

    Response response = Response.builder().status(status).reason(reason).request(request).build();

    return FeignException.errorStatus("HotelClient#lockRoom", response);
  }
}
