package ru.mephi.fsra.hotel.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.mephi.fsra.hotel.entity.Room;
import ru.mephi.fsra.hotel.service.RoomService;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

  private final RoomService roomService;
  private final ru.mephi.fsra.hotel.service.RoomStatisticsService statistics;

  public RoomController(
      RoomService roomService, ru.mephi.fsra.hotel.service.RoomStatisticsService statistics) {
    this.roomService = roomService;
    this.statistics = statistics;
  }

  // Создание номера в конкретной гостинице
  @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/hotel/{hotelId}")
  @ResponseStatus(HttpStatus.CREATED)
  public Room createRoom(@PathVariable("hotelId") Long hotelId, @RequestBody Room room) {

    return roomService.createRoom(hotelId, room);
  }

  // Получение всех номеров
  public record CreateRoomRequest(Long hotelId, String number, Boolean available) {}

  @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Room createRoom(@RequestBody CreateRoomRequest request) {
    if (request.hotelId() == null || request.number() == null || request.number().isBlank()) {
      throw new org.springframework.web.server.ResponseStatusException(
          HttpStatus.BAD_REQUEST, "hotelId and number are required");
    }
    Room room = new Room();
    room.setNumber(request.number());
    room.setAvailable(request.available() == null || request.available());
    return roomService.createRoom(request.hotelId(), room);
  }

  @GetMapping
  public List<Room> getAllRooms(
      @RequestParam(value = "checkIn", required = false)
          @org.springframework.format.annotation.DateTimeFormat(
              iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
          java.time.LocalDate checkIn,
      @RequestParam(value = "checkOut", required = false)
          @org.springframework.format.annotation.DateTimeFormat(
              iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
          java.time.LocalDate checkOut) {
    if (checkIn != null || checkOut != null) return roomService.recommendRooms(checkIn, checkOut);
    return roomService.getAvailableRooms();
  }

  // Получение доступных номеров
  @GetMapping("/available")
  public List<Room> getAvailableRooms() {
    return roomService.getAvailableRooms();
  }

  // Получение номеров конкретной гостиницы
  @GetMapping("/hotel/{hotelId}")
  public List<Room> getRoomsByHotel(@PathVariable("hotelId") Long hotelId) {

    return roomService.getRoomsByHotel(hotelId);
  }

  @GetMapping("/recommend")
  public List<Room> recommendRooms(
      @RequestParam("checkIn")
          @org.springframework.format.annotation.DateTimeFormat(
              iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
          java.time.LocalDate checkIn,
      @RequestParam("checkOut")
          @org.springframework.format.annotation.DateTimeFormat(
              iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
          java.time.LocalDate checkOut) {
    return roomService.recommendRooms(checkIn, checkOut);
  }

  @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
  @PutMapping("/{id}")
  public Room updateRoom(@PathVariable("id") Long id, @RequestBody Room room) {
    return roomService.updateRoom(id, room);
  }

  @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteRoom(@PathVariable("id") Long id) {
    roomService.deleteRoom(id);
  }

  @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/stats")
  public java.util.List<ru.mephi.fsra.hotel.service.RoomStatisticsService.RoomStatistics>
      roomStatistics(
          @RequestParam("from")
              @org.springframework.format.annotation.DateTimeFormat(
                  iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
              java.time.LocalDate from,
          @RequestParam("to")
              @org.springframework.format.annotation.DateTimeFormat(
                  iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
              java.time.LocalDate to) {
    return statistics.statistics(from, to);
  }

  @GetMapping("/{id}")
  public Room getRoomById(@PathVariable("id") Long id) {
    return roomService.getRoomById(id);
  }
}
