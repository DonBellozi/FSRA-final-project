package ru.mephi.fsra.booking.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import ru.mephi.fsra.booking.dto.RoomLockRequest;
import ru.mephi.fsra.booking.dto.RoomLockResponse;
import ru.mephi.fsra.booking.dto.RoomResponse;

@FeignClient(name = "hotel-service")
public interface HotelClient {

  @GetMapping("/api/rooms/recommend")
  java.util.List<RoomResponse> recommendRooms(
      @RequestParam("checkIn")
          @org.springframework.format.annotation.DateTimeFormat(
              iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
          java.time.LocalDate checkIn,
      @RequestParam("checkOut")
          @org.springframework.format.annotation.DateTimeFormat(
              iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
          java.time.LocalDate checkOut);

  @GetMapping("/api/rooms/{id}")
  RoomResponse getRoomById(@PathVariable("id") Long id);

  default RoomLockResponse lockRoom(RoomLockRequest request) {
    return confirmAvailability(request.roomId(), request);
  }

  @PostMapping("/api/rooms/{roomId}/confirm-availability")
  RoomLockResponse confirmAvailability(
      @PathVariable("roomId") Long roomId, @RequestBody RoomLockRequest request);

  record ReleaseRequest(Long bookingId) {}

  default void unlockRoom(Long roomId, Long bookingId) {
    releaseAvailability(roomId, new ReleaseRequest(bookingId));
  }

  @PostMapping("/api/rooms/{roomId}/release")
  void releaseAvailability(
      @PathVariable("roomId") Long roomId, @RequestBody ReleaseRequest request);

  @GetMapping("/api/room-locks/booking/{bookingId}")
  RoomLockResponse getLockByBookingId(@PathVariable("bookingId") Long bookingId);
}
