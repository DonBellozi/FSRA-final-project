package ru.mephi.fsra.hotel.service;

import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.mephi.fsra.hotel.entity.Room;
import ru.mephi.fsra.hotel.entity.RoomLock;
import ru.mephi.fsra.hotel.repository.RoomLockRepository;
import ru.mephi.fsra.hotel.repository.RoomRepository;

@Service
public class RoomLockService {

  private final RoomLockRepository roomLockRepository;
  private final RoomRepository roomRepository;
  private final ru.mephi.fsra.hotel.repository.ReservationGuardRepository guard;
  private final ru.mephi.fsra.hotel.repository.CancelledReservationRepository cancellations;

  public RoomLockService(
      RoomLockRepository roomLockRepository,
      RoomRepository roomRepository,
      ru.mephi.fsra.hotel.repository.ReservationGuardRepository guard,
      ru.mephi.fsra.hotel.repository.CancelledReservationRepository cancellations) {
    this.roomLockRepository = roomLockRepository;
    this.roomRepository = roomRepository;
    this.guard = guard;
    this.cancellations = cancellations;
  }

  @Transactional
  public RoomLock lockRoom(Long roomId, Long bookingId, LocalDate checkIn, LocalDate checkOut) {

    if (roomId == null
        || bookingId == null
        || checkIn == null
        || checkOut == null
        || !checkIn.isBefore(checkOut)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid room lock data");
    }

    guard.acquire();
    if (cancellations.existsById(bookingId)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking already released");
    }
    Room room =
        roomRepository
            .findByIdForUpdate(roomId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found: " + roomId));

    // Проверяем, нет ли блокировки с таким bookingId
    RoomLock existingLock = roomLockRepository.findByBookingId(bookingId).orElse(null);

    if (existingLock != null) {

      if (existingLock.getRoomId().equals(roomId)
          && existingLock.getCheckIn().equals(checkIn)
          && existingLock.getCheckOut().equals(checkOut)) {

        return existingLock;
      }

      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Booking ID already used for another room or dates");
    }

    if (!room.isAvailable()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Room is unavailable");
    }

    boolean occupied =
        roomLockRepository.existsByRoomIdAndCheckInLessThanAndCheckOutGreaterThan(
            roomId, checkOut, checkIn);

    if (occupied) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Room is already booked for these dates");
    }

    RoomLock lock = new RoomLock();
    lock.setRoomId(roomId);
    lock.setBookingId(bookingId);
    lock.setCheckIn(checkIn);
    lock.setCheckOut(checkOut);

    room.setTimesBooked(room.getTimesBooked() + 1);
    org.slf4j.LoggerFactory.getLogger(getClass())
        .info("bookingId={} roomId={} slot locked", bookingId, roomId);
    return roomLockRepository.save(lock);
  }

  @Transactional
  public void unlockRoom(Long roomId, Long bookingId) {
    if (roomId == null || bookingId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roomId and bookingId required");
    }
    guard.acquire();
    roomLockRepository
        .findByBookingId(bookingId)
        .ifPresent(
            lock -> {
              if (!lock.getRoomId().equals(roomId)) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Booking belongs to another room");
              }
            });
    unlockRoom(bookingId);
  }

  @Transactional
  public void unlockRoom(Long bookingId) {

    if (bookingId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking ID is required");
    }

    guard.acquire();
    if (!cancellations.existsById(bookingId)) {
      cancellations.save(new ru.mephi.fsra.hotel.entity.CancelledReservation(bookingId));
    }
    roomLockRepository.findByBookingId(bookingId).ifPresent(roomLockRepository::delete);
    org.slf4j.LoggerFactory.getLogger(getClass()).info("bookingId={} slot released", bookingId);
  }

  @Transactional(readOnly = true)
  public RoomLock getLockByBookingId(Long bookingId) {

    return roomLockRepository
        .findByBookingId(bookingId)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Room lock not found for booking: " + bookingId));
  }
}
