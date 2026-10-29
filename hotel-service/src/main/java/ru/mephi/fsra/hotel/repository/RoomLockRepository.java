package ru.mephi.fsra.hotel.repository;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.mephi.fsra.hotel.entity.RoomLock;

public interface RoomLockRepository extends JpaRepository<RoomLock, Long> {

  boolean existsByRoomIdAndCheckInLessThanAndCheckOutGreaterThan(
      Long roomId, LocalDate checkOut, LocalDate checkIn);

  java.util.List<RoomLock> findByCheckInLessThanAndCheckOutGreaterThan(
      LocalDate checkOut, LocalDate checkIn);

  boolean existsByRoomIdAndCheckOutGreaterThan(Long roomId, LocalDate date);

  Optional<RoomLock> findByBookingId(Long bookingId);
}
