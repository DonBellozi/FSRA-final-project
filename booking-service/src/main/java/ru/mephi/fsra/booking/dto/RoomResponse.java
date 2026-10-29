package ru.mephi.fsra.booking.dto;

public record RoomResponse(
    Long id, String number, boolean available, int timesBooked, HotelResponse hotel) {
  public record HotelResponse(Long id, String name, String address) {}
}
