package ru.mephi.fsra.booking.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import ru.mephi.fsra.booking.client.HotelClient;
import ru.mephi.fsra.booking.entity.Booking;
import ru.mephi.fsra.booking.repository.BookingRepository;

class CompensationRecoveryServiceTest {
  @Test
  void failedReleaseRemainsPendingUntilNextSuccessfulAttempt() {
    BookingRepository repository = mock(BookingRepository.class);
    HotelClient hotel = mock(HotelClient.class);
    Booking booking = new Booking();
    booking.setId(8L);
    booking.setRoomId(11L);
    booking.setCompensationPending(true);
    when(repository.findByCompensationPendingTrue()).thenReturn(java.util.List.of(booking));
    doThrow(mock(feign.FeignException.class)).doNothing().when(hotel).unlockRoom(11L, 8L);
    var recovery = new CompensationRecoveryService(repository, hotel);
    recovery.retryCompensations();
    assertTrue(booking.isCompensationPending());
    verify(repository, never()).acknowledgeCompensation(any());
    recovery.retryCompensations();
    assertFalse(booking.isCompensationPending());
    verify(hotel, times(2)).unlockRoom(11L, 8L);
    verify(repository).acknowledgeCompensation(8L);
  }

  @Test
  void releaseIsRetriedAndAcknowledged() {
    BookingRepository repository = mock(BookingRepository.class);
    HotelClient hotel = mock(HotelClient.class);
    Booking booking = new Booking();
    booking.setId(7L);
    booking.setTraceId("original-request-trace");
    booking.setRoomId(10L);
    booking.setCompensationPending(true);
    when(repository.findByCompensationPendingTrue()).thenReturn(java.util.List.of(booking));
    doAnswer(
            invocation -> {
              assertEquals("original-request-trace", org.slf4j.MDC.get("traceId"));
              return null;
            })
        .when(hotel)
        .unlockRoom(10L, 7L);
    new CompensationRecoveryService(repository, hotel).retryCompensations();
    assertNull(org.slf4j.MDC.get("traceId"));
    verify(hotel).unlockRoom(10L, 7L);
    assertFalse(booking.isCompensationPending());
    verify(repository).acknowledgeCompensation(7L);
  }
}
