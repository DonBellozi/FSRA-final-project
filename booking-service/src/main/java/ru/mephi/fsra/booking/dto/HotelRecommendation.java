package ru.mephi.fsra.booking.dto;

public record HotelRecommendation(
    Long hotelId,
    String hotelName,
    String address,
    long personalBookings,
    long totalBookings,
    long score,
    String reason) {}
