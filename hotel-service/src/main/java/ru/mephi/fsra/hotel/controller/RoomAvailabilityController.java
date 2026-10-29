package ru.mephi.fsra.hotel.controller;

import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.mephi.fsra.hotel.entity.RoomLock;
import ru.mephi.fsra.hotel.service.RoomLockService;

@RestController
@RequestMapping("/api/rooms/{roomId}")
public class RoomAvailabilityController {
  private final RoomLockService locks;

  public RoomAvailabilityController(RoomLockService locks) {
    this.locks = locks;
  }

  public record Confirmation(Long bookingId, LocalDate checkIn, LocalDate checkOut) {}

  public record Release(Long bookingId) {}

  @PostMapping("/confirm-availability")
  @ResponseStatus(HttpStatus.CREATED)
  public RoomLock confirm(@PathVariable("roomId") Long roomId, @RequestBody Confirmation request) {
    return locks.lockRoom(roomId, request.bookingId(), request.checkIn(), request.checkOut());
  }

  @PostMapping("/release")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void release(@PathVariable("roomId") Long roomId, @RequestBody Release request) {
    locks.unlockRoom(roomId, request.bookingId());
  }
}
