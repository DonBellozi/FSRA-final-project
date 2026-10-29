package ru.mephi.fsra.booking.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import ru.mephi.fsra.booking.client.HotelClient;
import ru.mephi.fsra.booking.entity.Booking;
import ru.mephi.fsra.booking.entity.BookingStatus;
import ru.mephi.fsra.booking.service.BookingService;

@WebMvcTest(BookingController.class)
class BookingControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private BookingService bookingService;

  @MockitoBean private HotelClient hotelClient;

  // Тест 1. Получение статистики администратором
  @Test
  void getBookingStats_shouldReturnHttp200AndCorrectJson() throws Exception {

    when(bookingService.getBookingStats())
        .thenReturn(
            Map.of(
                "totalBookings", 20L,
                "confirmedBookings", 15L,
                "cancelledBookings", 3L,
                "pendingBookings", 2L,
                "uniqueUsers", 5L,
                "uniqueRooms", 10L));

    mockMvc
        .perform(
            get("/api/bookings/stats")
                .with(
                    jwt()
                        .jwt(
                            token ->
                                token.subject("admin").claim("userId", 1L).claim("role", "ADMIN"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.totalBookings").value(20))
        .andExpect(jsonPath("$.confirmedBookings").value(15))
        .andExpect(jsonPath("$.cancelledBookings").value(3))
        .andExpect(jsonPath("$.pendingBookings").value(2))
        .andExpect(jsonPath("$.uniqueUsers").value(5))
        .andExpect(jsonPath("$.uniqueRooms").value(10));
  }

  // Тест 2. Создание бронирования через REST API
  @Test
  void createBooking_shouldReturnHttp201AndConfirmedBooking() throws Exception {

    String idempotencyKey = "rest-test-key-001";

    Booking confirmedBooking = new Booking();
    confirmedBooking.setId(200L);
    confirmedBooking.setUserId(1L);
    confirmedBooking.setRoomId(10L);
    confirmedBooking.setCheckIn(LocalDate.of(2026, 11, 10));
    confirmedBooking.setCheckOut(LocalDate.of(2026, 11, 15));
    confirmedBooking.setIdempotencyKey(idempotencyKey);
    confirmedBooking.setStatus(BookingStatus.CONFIRMED);

    when(bookingService.createBooking(any(Booking.class), eq(idempotencyKey)))
        .thenReturn(confirmedBooking);

    // Намеренно передаём чужой userId в JSON.
    // Контроллер должен заменить его на userId из JWT.
    String requestJson =
        """
                {
                    "userId": 999,
                    "roomId": 10,
                    "checkIn": "2026-11-10",
                    "checkOut": "2026-11-15"
                }
                """;

    mockMvc
        .perform(
            post("/api/bookings")
                .with(
                    jwt()
                        .jwt(
                            token ->
                                token
                                    .subject("test-user")
                                    .claim("userId", 1L)
                                    .claim("role", "USER"))
                        .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isCreated())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(200))
        .andExpect(jsonPath("$.userId").value(1))
        .andExpect(jsonPath("$.roomId").value(10))
        .andExpect(jsonPath("$.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.idempotencyKey").value(idempotencyKey));

    // Проверяем, что сервис получил userId из JWT,
    // а не значение 999 из тела запроса.
    verify(bookingService, times(1))
        .createBooking(
            argThat(booking -> Long.valueOf(1L).equals(booking.getUserId())), eq(idempotencyKey));
  }

  // Тест 3. Отсутствует Idempotency-Key
  @Test
  void createBooking_shouldReturnHttp400_whenIdempotencyKeyMissing() throws Exception {

    String requestJson =
        """
                {
                    "userId": 1,
                    "roomId": 10,
                    "checkIn": "2026-11-10",
                    "checkOut": "2026-11-15"
                }
                """;

    mockMvc
        .perform(
            post("/api/bookings")
                .with(
                    jwt()
                        .jwt(
                            token ->
                                token
                                    .subject("test-user")
                                    .claim("userId", 1L)
                                    .claim("role", "USER"))
                        .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isBadRequest());

    verify(bookingService, never()).createBooking(any(Booking.class), any());
  }

  // Тест 4. Некорректные даты бронирования
  @Test
  void createBooking_shouldReturnHttp400_whenDatesAreInvalid() throws Exception {

    String idempotencyKey = "rest-test-invalid-dates";

    String requestJson =
        """
                {
                    "userId": 1,
                    "roomId": 10,
                    "checkIn": "2026-11-15",
                    "checkOut": "2026-11-10"
                }
                """;

    when(bookingService.createBooking(any(Booking.class), eq(idempotencyKey)))
        .thenThrow(
            new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Check-out must be after check-in"));

    mockMvc
        .perform(
            post("/api/bookings")
                .with(
                    jwt()
                        .jwt(
                            token ->
                                token
                                    .subject("test-user")
                                    .claim("userId", 1L)
                                    .claim("role", "USER"))
                        .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isBadRequest());

    verify(bookingService, times(1)).createBooking(any(Booking.class), eq(idempotencyKey));
  }
}
