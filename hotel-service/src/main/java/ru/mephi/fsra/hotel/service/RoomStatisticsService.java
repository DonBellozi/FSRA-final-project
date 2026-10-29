package ru.mephi.fsra.hotel.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.mephi.fsra.hotel.repository.*;

@Service
public class RoomStatisticsService {
  private final RoomRepository rooms;
  private final RoomLockRepository locks;

  public RoomStatisticsService(RoomRepository rooms, RoomLockRepository locks) {
    this.rooms = rooms;
    this.locks = locks;
  }

  public record RoomStatistics(
      Long roomId,
      Long hotelId,
      String number,
      boolean available,
      int timesBooked,
      long bookingsInPeriod,
      long occupiedNights,
      long periodNights,
      double occupancyPercent) {}

  @Transactional(readOnly = true)
  public List<RoomStatistics> statistics(LocalDate from, LocalDate to) {
    if (from == null || to == null || !from.isBefore(to))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must precede to");
    long nights = ChronoUnit.DAYS.between(from, to);
    Map<Long, Long> occupied = new HashMap<>();
    Map<Long, Long> counts = new HashMap<>();
    for (var lock : locks.findByCheckInLessThanAndCheckOutGreaterThan(to, from)) {
      LocalDate start = lock.getCheckIn().isBefore(from) ? from : lock.getCheckIn();
      LocalDate end = lock.getCheckOut().isAfter(to) ? to : lock.getCheckOut();
      occupied.merge(lock.getRoomId(), ChronoUnit.DAYS.between(start, end), Long::sum);
      counts.merge(lock.getRoomId(), 1L, Long::sum);
    }
    return rooms.findAll().stream()
        .sorted(Comparator.comparing(r -> r.getId()))
        .map(
            r ->
                new RoomStatistics(
                    r.getId(),
                    r.getHotel().getId(),
                    r.getNumber(),
                    r.isAvailable(),
                    r.getTimesBooked(),
                    counts.getOrDefault(r.getId(), 0L),
                    occupied.getOrDefault(r.getId(), 0L),
                    nights,
                    Math.round(occupied.getOrDefault(r.getId(), 0L) * 10000.0 / nights) / 100.0))
        .toList();
  }
}
