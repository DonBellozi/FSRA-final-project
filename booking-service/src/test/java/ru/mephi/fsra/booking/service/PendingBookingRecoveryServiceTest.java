package ru.mephi.fsra.booking.service;

import static org.mockito.Mockito.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import ru.mephi.fsra.booking.repository.BookingRepository;

class PendingBookingRecoveryServiceTest {
  @Test
  void recoveryExpiresRequestsInsteadOfConfirmingThem() {
    BookingRepository repository = mock(BookingRepository.class);
    Instant before = Instant.now().minusSeconds(120);
    new PendingBookingRecoveryService(repository).recoverPendingBookings();
    Instant after = Instant.now().minusSeconds(120);
    verify(repository)
        .cancelStalePending(argThat(cutoff -> !cutoff.isBefore(before) && !cutoff.isAfter(after)));
    verifyNoMoreInteractions(repository);
  }
}
