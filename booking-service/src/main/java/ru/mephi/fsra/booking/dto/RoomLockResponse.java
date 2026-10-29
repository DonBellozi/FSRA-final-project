package ru.mephi.fsra.booking.dto;

import java.time.LocalDate;

public record RoomLockResponse(
    Long id, Long roomId, Long bookingId, LocalDate checkIn, LocalDate checkOut) {}
