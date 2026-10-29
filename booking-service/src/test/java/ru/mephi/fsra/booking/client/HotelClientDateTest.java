package ru.mephi.fsra.booking.client;

import static org.junit.jupiter.api.Assertions.*;

import feign.Feign;
import feign.Response;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.format.support.DefaultFormattingConversionService;

class HotelClientDateTest {
  @Test
  void recommendationDatesAreIsoRegardlessOfLocale() {
    AtomicReference<String> url = new AtomicReference<>();
    HotelClient client =
        Feign.builder()
            .contract(new SpringMvcContract(List.of(), new DefaultFormattingConversionService()))
            .encoder(
                new org.springframework.cloud.openfeign.support.SpringEncoder(
                    () -> new org.springframework.boot.autoconfigure.http.HttpMessageConverters()))
            .client(
                (request, options) -> {
                  url.set(request.url());
                  return Response.builder()
                      .request(request)
                      .status(200)
                      .headers(Map.of())
                      .body("[]", java.nio.charset.StandardCharsets.UTF_8)
                      .build();
                })
            .decoder(
                (response, type) ->
                    type == ru.mephi.fsra.booking.dto.RoomLockResponse.class
                        ? new ru.mephi.fsra.booking.dto.RoomLockResponse(
                            1L, 7L, 42L, LocalDate.of(2027, 2, 10), LocalDate.of(2027, 2, 12))
                        : List.of())
            .target(HotelClient.class, "http://hotel-test");
    Locale original = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("ru-RU"));
      client.recommendRooms(LocalDate.of(2027, 2, 10), LocalDate.of(2027, 2, 12));
      assertTrue(url.get().contains("checkIn=2027-02-10"), url.get());
      assertTrue(url.get().contains("checkOut=2027-02-12"), url.get());
      client.lockRoom(
          new ru.mephi.fsra.booking.dto.RoomLockRequest(
              7L, 42L, LocalDate.of(2027, 2, 10), LocalDate.of(2027, 2, 12)));
      assertTrue(url.get().endsWith("/api/rooms/7/confirm-availability"), url.get());
      client.unlockRoom(7L, 42L);
      assertTrue(url.get().endsWith("/api/rooms/7/release"), url.get());
    } finally {
      Locale.setDefault(original);
    }
  }
}
