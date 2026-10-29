package ru.mephi.fsra.booking.service;

import java.time.Instant;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.mephi.fsra.booking.repository.BookingRepository;

/** Incomplete requests expire; compensation recovery releases their Hotel slots. */
@Service
public class PendingBookingRecoveryService {
  private final BookingRepository repository;

  public PendingBookingRecoveryService(BookingRepository repository) {
    this.repository = repository;
  }

  @Scheduled(fixedDelayString = "${app.pending-recovery.delay:30000}")
  public void recoverPendingBookings() {
    int cancelled = repository.cancelStalePending(Instant.now().minusSeconds(120));
    if (cancelled > 0)
      LoggerFactory.getLogger(getClass()).info("Expired PENDING bookings: {}", cancelled);
  }
}
