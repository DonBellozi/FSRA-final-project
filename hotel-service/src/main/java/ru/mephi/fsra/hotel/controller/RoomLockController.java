package ru.mephi.fsra.hotel.controller;

import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.mephi.fsra.hotel.entity.RoomLock;
import ru.mephi.fsra.hotel.service.RoomLockService;

@RestController
@RequestMapping("/api/room-locks")
public class RoomLockController {

  private final RoomLockService roomLockService;

  public RoomLockController(RoomLockService roomLockService) {
    this.roomLockService = roomLockService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public RoomLock lockRoom(@RequestBody LockRequest request) {
    return roomLockService.lockRoom(
        request.roomId(), request.bookingId(), request.checkIn(), request.checkOut());
  }

  @DeleteMapping("/booking/{bookingId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unlockRoom(@PathVariable("bookingId") Long bookingId) {
    roomLockService.unlockRoom(bookingId);
  }

  @GetMapping("/booking/{bookingId}")
  public RoomLock getLockByBookingId(@PathVariable("bookingId") Long bookingId) {

    return roomLockService.getLockByBookingId(bookingId);
  }

  public record LockRequest(Long roomId, Long bookingId, LocalDate checkIn, LocalDate checkOut) {}
}
