package ru.mephi.fsra.booking.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import ru.mephi.fsra.booking.entity.*;
import ru.mephi.fsra.booking.repository.BookingRepository;

@DataJpaTest
class BookingTransitionsTest {
  @Autowired BookingRepository repository;

  @Test
  void expiryCancelsOnlyOldPendingAndQueuesCompensation() {
    Booking pending = new Booking();
    pending.setUserId(1L);
    pending.setRoomId(1L);
    pending.setCheckIn(LocalDate.of(2027, 1, 1));
    pending.setCheckOut(LocalDate.of(2027, 1, 2));
    org.springframework.test.util.ReflectionTestUtils.setField(
        pending, "createdAt", java.time.Instant.now().minusSeconds(180));
    Long pendingId = repository.saveAndFlush(pending).getId();
    Booking confirmed = new Booking();
    confirmed.setUserId(1L);
    confirmed.setRoomId(2L);
    confirmed.setCheckIn(LocalDate.of(2027, 1, 1));
    confirmed.setCheckOut(LocalDate.of(2027, 1, 2));
    confirmed.setStatus(BookingStatus.CONFIRMED);
    org.springframework.test.util.ReflectionTestUtils.setField(
        confirmed, "createdAt", java.time.Instant.now().minusSeconds(180));
    Long confirmedId = repository.saveAndFlush(confirmed).getId();
    assertEquals(1, repository.cancelStalePending(java.time.Instant.now().minusSeconds(120)));
    assertEquals(BookingStatus.CANCELLED, repository.findById(pendingId).orElseThrow().getStatus());
    assertTrue(repository.findById(pendingId).orElseThrow().isCompensationPending());
    assertEquals(
        BookingStatus.CONFIRMED, repository.findById(confirmedId).orElseThrow().getStatus());
    assertEquals(0, repository.confirmPending(pendingId));
  }

  @Test
  void cancellationCannotBeOverwrittenByLateConfirmation() {
    Booking booking = new Booking();
    booking.setUserId(1L);
    booking.setRoomId(1L);
    booking.setCheckIn(LocalDate.of(2027, 1, 1));
    booking.setCheckOut(LocalDate.of(2027, 1, 2));
    Long id = repository.saveAndFlush(booking).getId();
    assertEquals(1, repository.requestCancellation(id));
    assertEquals(0, repository.confirmPending(id));
    Booking cancelled = repository.findById(id).orElseThrow();
    assertEquals(BookingStatus.CANCELLED, cancelled.getStatus());
    assertTrue(cancelled.isCompensationPending());
    assertEquals(1, repository.acknowledgeCompensation(id));
    assertFalse(repository.findById(id).orElseThrow().isCompensationPending());
    assertEquals(0, repository.confirmPending(id));
  }
}
