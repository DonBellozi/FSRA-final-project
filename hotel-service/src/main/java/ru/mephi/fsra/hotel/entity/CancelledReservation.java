package ru.mephi.fsra.hotel.entity;

import jakarta.persistence.*;

/** Tombstone prevents a delayed confirmation from reviving a released booking. */
@Entity
public class CancelledReservation {
  @Id private Long id;

  public CancelledReservation() {}

  public CancelledReservation(Long id) {
    this.id = id;
  }
}
