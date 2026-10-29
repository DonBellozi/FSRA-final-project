package ru.mephi.fsra.hotel.entity;

import jakarta.persistence.*;

/** Serializes lock/release transactions in the educational H2 application. */
@Entity
public class ReservationGuard {
  @Id private Long id;

  public ReservationGuard() {}

  public ReservationGuard(Long id) {
    this.id = id;
  }
}
