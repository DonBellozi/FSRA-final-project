package ru.mephi.fsra.booking.dto;

import java.time.LocalDate;

public record RoomLockRequest(Long roomId, Long bookingId, LocalDate checkIn, LocalDate checkOut) {}
