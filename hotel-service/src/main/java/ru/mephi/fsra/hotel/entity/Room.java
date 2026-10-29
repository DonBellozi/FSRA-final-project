package ru.mephi.fsra.hotel.entity;

import jakarta.persistence.*;

@Entity
@Table(
    name = "rooms",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_room_hotel_number",
            columnNames = {"hotel_id", "number"}))
public class Room {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "hotel_id", nullable = false)
  private Hotel hotel;

  @Column(nullable = false)
  private String number;

  @Column(nullable = false)
  private boolean available = true;

  @Column(name = "times_booked", nullable = false)
  private int timesBooked = 0;

  public Room() {}

  public Room(Hotel hotel, String number) {
    this.hotel = hotel;
    this.number = number;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Hotel getHotel() {
    return hotel;
  }

  public void setHotel(Hotel hotel) {
    this.hotel = hotel;
  }

  public String getNumber() {
    return number;
  }

  public void setNumber(String number) {
    this.number = number;
  }

  public boolean isAvailable() {
    return available;
  }

  public void setAvailable(boolean available) {
    this.available = available;
  }

  public int getTimesBooked() {
    return timesBooked;
  }

  public void setTimesBooked(int timesBooked) {
    this.timesBooked = timesBooked;
  }
}
