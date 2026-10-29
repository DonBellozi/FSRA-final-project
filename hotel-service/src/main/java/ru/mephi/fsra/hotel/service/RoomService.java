package ru.mephi.fsra.hotel.service;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.mephi.fsra.hotel.entity.Hotel;
import ru.mephi.fsra.hotel.entity.Room;
import ru.mephi.fsra.hotel.repository.HotelRepository;
import ru.mephi.fsra.hotel.repository.RoomRepository;

@Service
public class RoomService {

  private final RoomRepository roomRepository;
  private final HotelRepository hotelRepository;
  private final ru.mephi.fsra.hotel.repository.RoomLockRepository locks;
  private final ru.mephi.fsra.hotel.repository.ReservationGuardRepository guard;

  public RoomService(
      RoomRepository roomRepository,
      HotelRepository hotelRepository,
      ru.mephi.fsra.hotel.repository.RoomLockRepository locks,
      ru.mephi.fsra.hotel.repository.ReservationGuardRepository guard) {
    this.roomRepository = roomRepository;
    this.hotelRepository = hotelRepository;
    this.locks = locks;
    this.guard = guard;
  }

  @Transactional
  public Room createRoom(Long hotelId, Room room) {
    Hotel hotel =
        hotelRepository
            .findById(hotelId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Hotel not found: " + hotelId));

    room.setId(null);
    room.setHotel(hotel);
    room.setTimesBooked(0);

    return roomRepository.save(room);
  }

  @Transactional(readOnly = true)
  public List<Room> recommendRooms(java.time.LocalDate checkIn, java.time.LocalDate checkOut) {
    if (checkIn == null || checkOut == null || !checkIn.isBefore(checkOut)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid dates");
    }
    return roomRepository.findFreeRooms(checkIn, checkOut);
  }

  @Transactional
  public Room updateRoom(Long id, Room input) {
    Room room = getRoomById(id);
    room.setNumber(input.getNumber());
    room.setAvailable(input.isAvailable());
    return roomRepository.save(room);
  }

  @Transactional
  public void deleteRoom(Long id) {
    guard.acquire();
    Room room = getRoomById(id);
    if (locks.existsByRoomIdAndCheckOutGreaterThan(id, java.time.LocalDate.now())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Room has active or future bookings");
    }
    roomRepository.delete(room);
  }

  @Transactional(readOnly = true)
  public List<Room> getAllRooms() {
    return roomRepository.findAll();
  }

  @Transactional(readOnly = true)
  public List<Room> getAvailableRooms() {
    return roomRepository.findByAvailableTrue();
  }

  @Transactional(readOnly = true)
  public List<Room> getRoomsByHotel(Long hotelId) {
    return roomRepository.findByHotelId(hotelId);
  }

  @Transactional(readOnly = true)
  public Room getRoomById(Long id) {
    return roomRepository
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found: " + id));
  }
}
