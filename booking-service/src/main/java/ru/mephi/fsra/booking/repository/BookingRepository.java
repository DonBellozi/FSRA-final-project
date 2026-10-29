package ru.mephi.fsra.booking.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.mephi.fsra.booking.entity.Booking;
import ru.mephi.fsra.booking.entity.BookingStatus;

public interface BookingRepository extends JpaRepository<Booking, Long> {

  @org.springframework.transaction.annotation.Transactional
  @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
  @org.springframework.data.jpa.repository.Query(
      "update Booking b set b.status = ru.mephi.fsra.booking.entity.BookingStatus.CONFIRMED where b.id = :id and b.status = ru.mephi.fsra.booking.entity.BookingStatus.PENDING")
  int confirmPending(@org.springframework.data.repository.query.Param("id") Long id);

  @org.springframework.transaction.annotation.Transactional
  @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
  @org.springframework.data.jpa.repository.Query(
      "update Booking b set b.status = ru.mephi.fsra.booking.entity.BookingStatus.CANCELLED, b.compensationPending = true where b.id = :id")
  int requestCancellation(@org.springframework.data.repository.query.Param("id") Long id);

  @org.springframework.transaction.annotation.Transactional
  @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
  @org.springframework.data.jpa.repository.Query(
      "update Booking b set b.compensationPending = false where b.id = :id and b.status = ru.mephi.fsra.booking.entity.BookingStatus.CANCELLED")
  int acknowledgeCompensation(@org.springframework.data.repository.query.Param("id") Long id);

  @org.springframework.transaction.annotation.Transactional
  @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
  @org.springframework.data.jpa.repository.Query(
      "update Booking b set b.status = ru.mephi.fsra.booking.entity.BookingStatus.CANCELLED, b.compensationPending = true where b.status = ru.mephi.fsra.booking.entity.BookingStatus.PENDING and b.createdAt < :cutoff")
  int cancelStalePending(
      @org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff);

  List<Booking> findByCompensationPendingTrue();

  Optional<Booking> findByIdempotencyKey(String idempotencyKey);

  List<Booking> findByStatus(BookingStatus status);

  org.springframework.data.domain.Page<Booking> findByUserId(
      Long userId, org.springframework.data.domain.Pageable pageable);

  List<Booking> findByUserIdOrderByCheckInDesc(Long userId);
}
