package ru.mephi.fsra.hotel.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.fsra.hotel.entity.Hotel;
import ru.mephi.fsra.hotel.repository.HotelRepository;

@Service
public class HotelService {

  private final HotelRepository hotelRepository;
  private final ru.mephi.fsra.hotel.repository.RoomRepository rooms;

  public HotelService(
      HotelRepository hotelRepository, ru.mephi.fsra.hotel.repository.RoomRepository rooms) {
    this.hotelRepository = hotelRepository;
    this.rooms = rooms;
  }

  @Transactional
  public Hotel createHotel(Hotel hotel) {
    hotel.setId(null);
    return hotelRepository.save(hotel);
  }

  @Transactional
  public Hotel updateHotel(Long id, Hotel input) {
    Hotel hotel = getHotelById(id);
    hotel.setName(input.getName());
    hotel.setAddress(input.getAddress());
    return hotelRepository.save(hotel);
  }

  @Transactional
  public void deleteHotel(Long id) {
    Hotel hotel = getHotelById(id);
    if (rooms.existsByHotelId(id)) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.CONFLICT, "Delete hotel rooms first");
    }
    hotelRepository.delete(hotel);
  }

  @Transactional(readOnly = true)
  public List<Hotel> getAllHotels() {
    return hotelRepository.findAll();
  }

  @Transactional(readOnly = true)
  public Hotel getHotelById(Long id) {
    return hotelRepository
        .findById(id)
        .orElseThrow(
            () ->
                new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Hotel not found: " + id));
  }
}
