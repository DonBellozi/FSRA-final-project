package ru.mephi.fsra.booking.service;

import feign.FeignException;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.mephi.fsra.booking.client.HotelClient;
import ru.mephi.fsra.booking.repository.BookingRepository;

@Service
public class CompensationRecoveryService {
  private final BookingRepository repository;
  private final HotelClient hotel;

  public CompensationRecoveryService(BookingRepository repository, HotelClient hotel) {
    this.repository = repository;
    this.hotel = hotel;
  }

  @Scheduled(fixedDelayString = "${app.compensation.delay:30000}")
  public void retryCompensations() {
    for (var booking : repository.findByCompensationPendingTrue()) {
      String traceId =
          booking.getTraceId() == null
              ? java.util.UUID.randomUUID().toString()
              : booking.getTraceId();
      try (var context = org.slf4j.MDC.putCloseable("traceId", traceId)) {
        hotel.unlockRoom(booking.getRoomId(), booking.getId());
        booking.setCompensationPending(false);
        repository.acknowledgeCompensation(booking.getId());
        LoggerFactory.getLogger(getClass())
            .info("bookingId={} compensation completed", booking.getId());
      } catch (FeignException e) {
        LoggerFactory.getLogger(getClass())
            .warn("bookingId={} compensation deferred", booking.getId());
      }
    }
  }
}
