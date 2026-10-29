package ru.mephi.fsra.booking.service;

import feign.FeignException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.mephi.fsra.booking.client.HotelClient;
import ru.mephi.fsra.booking.dto.HotelRecommendation;
import ru.mephi.fsra.booking.dto.RoomLockRequest;
import ru.mephi.fsra.booking.dto.RoomResponse;
import ru.mephi.fsra.booking.entity.Booking;
import ru.mephi.fsra.booking.entity.BookingStatus;
import ru.mephi.fsra.booking.repository.BookingRepository;

@Service
public class BookingService {

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(BookingService.class);
  private final BookingRepository bookingRepository;
  private final HotelClient hotelClient;

  public BookingService(BookingRepository bookingRepository, HotelClient hotelClient) {
    this.bookingRepository = bookingRepository;
    this.hotelClient = hotelClient;
  }

  public Booking createBooking(Booking booking, String idempotencyKey) {

    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key is required");
    }

    if (booking.getUserId() == null
        || (!booking.isAutoSelect() && booking.getRoomId() == null)
        || booking.getCheckIn() == null
        || booking.getCheckOut() == null
        || !booking.getCheckIn().isBefore(booking.getCheckOut())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid booking data");
    }

    // Повторный запрос: возвращаем существующее бронирование
    Booking existing = bookingRepository.findByIdempotencyKey(idempotencyKey).orElse(null);

    if (existing != null) {
      if (existing.isAutoSelect() != booking.isAutoSelect()
          || !Objects.equals(existing.getUserId(), booking.getUserId())
          || (!booking.isAutoSelect() && !Objects.equals(existing.getRoomId(), booking.getRoomId()))
          || !Objects.equals(existing.getCheckIn(), booking.getCheckIn())
          || !Objects.equals(existing.getCheckOut(), booking.getCheckOut())) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Idempotency-Key already used with different data");
      }

      return existing;
    }

    if (booking.isAutoSelect()) {
      List<RoomResponse> candidates;
      try {
        candidates = hotelClient.recommendRooms(booking.getCheckIn(), booking.getCheckOut());
      } catch (FeignException e) {
        throw new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE, "Hotel recommendations temporarily unavailable", e);
      }
      if (candidates.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "No rooms available");
      }
      booking.setRoomId(candidates.get(0).id());
    }

    booking.setId(null);
    String traceId = org.slf4j.MDC.get("traceId");
    booking.setTraceId(traceId == null ? java.util.UUID.randomUUID().toString() : traceId);
    booking.setIdempotencyKey(idempotencyKey);
    booking.setStatus(BookingStatus.PENDING);

    try {
      booking = bookingRepository.saveAndFlush(booking);
      log.info("bookingId={} status=PENDING roomId={}", booking.getId(), booking.getRoomId());
    } catch (DataIntegrityViolationException e) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Concurrent request with the same Idempotency-Key");
    }

    FeignException failure = null;
    for (int attempt = 0; attempt < 3; attempt++) {
      try {
        hotelClient.lockRoom(
            new RoomLockRequest(
                booking.getRoomId(), booking.getId(), booking.getCheckIn(), booking.getCheckOut()));
        if (bookingRepository.confirmPending(booking.getId()) == 0) {
          hotelClient.unlockRoom(booking.getRoomId(), booking.getId());
          throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking already cancelled");
        }
        booking.setStatus(BookingStatus.CONFIRMED);
        log.info("bookingId={} status=CONFIRMED", booking.getId());
        return booking;
      } catch (FeignException e) {
        failure = e;
        if (e.status() >= 400 && e.status() < 500) break;
        if (attempt == 2) break;
        try {
          Thread.sleep(100L << attempt);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }
    bookingRepository.requestCancellation(booking.getId());
    log.info("bookingId={} status=CANCELLED compensationPending=true", booking.getId());
    booking.setStatus(BookingStatus.CANCELLED);
    booking.setCompensationPending(true);
    try {
      hotelClient.unlockRoom(booking.getRoomId(), booking.getId());
      bookingRepository.acknowledgeCompensation(booking.getId());
      log.info("bookingId={} compensation completed", booking.getId());
      booking.setCompensationPending(false);
    } catch (FeignException compensationFailure) {
      org.slf4j.LoggerFactory.getLogger(getClass())
          .error("bookingId={} compensation failed", booking.getId(), compensationFailure);
    }
    throw new ResponseStatusException(
        failure != null && failure.status() == 409
            ? HttpStatus.CONFLICT
            : HttpStatus.SERVICE_UNAVAILABLE,
        "Booking cancelled");
  }

  // Отмена бронирования
  public Booking cancelBooking(Long bookingId) {

    Booking booking =
        bookingRepository
            .findById(bookingId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found: " + bookingId));

    // Повторная отмена не должна приводить к ошибке
    if (booking.getStatus() == BookingStatus.CANCELLED) {
      return booking;
    }

    bookingRepository.requestCancellation(booking.getId());
    log.info("bookingId={} status=CANCELLED compensationPending=true", booking.getId());
    booking.setStatus(BookingStatus.CANCELLED);
    booking.setCompensationPending(true);
    try {
      hotelClient.unlockRoom(booking.getRoomId(), bookingId);
      bookingRepository.acknowledgeCompensation(bookingId);
      log.info("bookingId={} compensation completed", bookingId);
      booking.setCompensationPending(false);
      return booking;
    } catch (FeignException e) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Cancellation saved; release will be retried", e);
    }
  }

  public record BookingPage(
      List<Booking> content, int page, int size, long totalElements, int totalPages) {}

  @Transactional(readOnly = true)
  public BookingPage getBookingPage(Long userId, Integer page, Integer size) {
    int pageNumber = page == null ? 0 : page;
    int pageSize = size == null ? 20 : size;
    if (pageNumber < 0 || pageSize < 1 || pageSize > 100) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "page >= 0, size between 1 and 100 required");
    }
    var pageable =
        org.springframework.data.domain.PageRequest.of(
            pageNumber,
            pageSize,
            org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.desc("checkIn"),
                org.springframework.data.domain.Sort.Order.desc("id")));
    var result =
        userId == null
            ? bookingRepository.findAll(pageable)
            : bookingRepository.findByUserId(userId, pageable);
    return new BookingPage(
        result.getContent(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }

  @Transactional(readOnly = true)
  public List<Booking> getAllBookings() {
    return bookingRepository.findAll();
  }

  @Transactional(readOnly = true)
  public Booking getBookingById(Long id) {
    return bookingRepository
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found: " + id));
  }

  @Transactional(readOnly = true)
  public List<Booking> getBookingsByUserId(Long userId) {
    return bookingRepository.findByUserIdOrderByCheckInDesc(userId);
  }

  @Transactional(readOnly = true)
  public Map<String, Long> getBookingStats() {

    List<Booking> bookings = bookingRepository.findAll();

    long confirmed =
        bookings.stream().filter(b -> b.getStatus() == BookingStatus.CONFIRMED).count();

    long cancelled =
        bookings.stream().filter(b -> b.getStatus() == BookingStatus.CANCELLED).count();

    long pending = bookings.stream().filter(b -> b.getStatus() == BookingStatus.PENDING).count();

    long uniqueUsers = bookings.stream().map(Booking::getUserId).distinct().count();

    long uniqueRooms = bookings.stream().map(Booking::getRoomId).distinct().count();

    return Map.of(
        "totalBookings", (long) bookings.size(),
        "confirmedBookings", confirmed,
        "cancelledBookings", cancelled,
        "pendingBookings", pending,
        "uniqueUsers", uniqueUsers,
        "uniqueRooms", uniqueRooms);
  }

  @Transactional(readOnly = true)
  public List<HotelRecommendation> getHotelRecommendations(Long userId) {

    List<Booking> bookings = bookingRepository.findByStatus(BookingStatus.CONFIRMED);

    Map<Long, RoomResponse.HotelResponse> hotels = new HashMap<>();
    Map<Long, Long> totalBookings = new HashMap<>();
    Map<Long, Long> personalBookings = new HashMap<>();
    Map<Long, RoomResponse> rooms = new HashMap<>();

    for (Booking booking : bookings) {

      Long roomId = booking.getRoomId();

      RoomResponse room = rooms.computeIfAbsent(roomId, hotelClient::getRoomById);

      if (room == null || room.hotel() == null) {
        continue;
      }

      RoomResponse.HotelResponse hotel = room.hotel();
      Long hotelId = hotel.id();

      hotels.put(hotelId, hotel);
      totalBookings.merge(hotelId, 1L, Long::sum);

      if (Objects.equals(booking.getUserId(), userId)) {
        personalBookings.merge(hotelId, 1L, Long::sum);
      }
    }

    List<HotelRecommendation> recommendations = new ArrayList<>();

    for (Long hotelId : hotels.keySet()) {

      RoomResponse.HotelResponse hotel = hotels.get(hotelId);

      long total = totalBookings.getOrDefault(hotelId, 0L);
      long personal = personalBookings.getOrDefault(hotelId, 0L);
      long others = total - personal;

      long score = personal * 10 + others * 2;

      String reason =
          personal > 0 ? "Вы уже бронировали этот отель" : "Популярный среди других пользователей";

      recommendations.add(
          new HotelRecommendation(
              hotelId, hotel.name(), hotel.address(), personal, total, score, reason));
    }

    recommendations.sort(Comparator.comparingLong(HotelRecommendation::score).reversed());

    return recommendations;
  }
}
