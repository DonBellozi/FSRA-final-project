package ru.mephi.fsra.hotel.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.mephi.fsra.hotel.entity.Hotel;
import ru.mephi.fsra.hotel.service.HotelService;

@RestController
@RequestMapping("/api/hotels")
public class HotelController {

  private final HotelService hotelService;

  public HotelController(HotelService hotelService) {
    this.hotelService = hotelService;
  }

  @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Hotel createHotel(@RequestBody Hotel hotel) {
    return hotelService.createHotel(hotel);
  }

  @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
  @PutMapping("/{id}")
  public Hotel updateHotel(@PathVariable("id") Long id, @RequestBody Hotel hotel) {
    return hotelService.updateHotel(id, hotel);
  }

  @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteHotel(@PathVariable("id") Long id) {
    hotelService.deleteHotel(id);
  }

  @GetMapping
  public List<Hotel> getAllHotels() {
    return hotelService.getAllHotels();
  }

  @GetMapping("/{id}")
  public Hotel getHotelById(@PathVariable("id") Long id) {
    return hotelService.getHotelById(id);
  }
}
