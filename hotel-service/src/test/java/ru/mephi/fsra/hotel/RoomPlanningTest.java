package ru.mephi.fsra.hotel;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;
import ru.mephi.fsra.hotel.entity.*;
import ru.mephi.fsra.hotel.repository.*;
import ru.mephi.fsra.hotel.service.*;

@SpringBootTest(
    properties = {"eureka.client.enabled=false", "spring.datasource.url=jdbc:h2:mem:planning-test"})
class RoomPlanningTest {
  @Autowired HotelRepository hotels;
  @Autowired RoomRepository rooms;
  @Autowired RoomService roomService;
  @Autowired RoomLockService locks;

  @Test
  void recommendationsRespectDatesCounterAndIdempotency() throws Exception {
    Hotel hotel = new Hotel();
    hotel.setName("Planning test");
    hotel.setAddress("Test address");
    hotel = hotels.saveAndFlush(hotel);
    Room first = rooms.saveAndFlush(new Room(hotel, "1"));
    Room second = rooms.saveAndFlush(new Room(hotel, "2"));
    LocalDate start = LocalDate.of(2027, 1, 10);
    LocalDate end = start.plusDays(3);
    assertEquals(first.getId(), roomService.recommendRooms(start, end).get(0).getId());
    locks.lockRoom(first.getId(), 9001L, start, end);
    locks.lockRoom(first.getId(), 9001L, start, end);
    assertEquals(1, rooms.findById(first.getId()).orElseThrow().getTimesBooked());
    assertEquals(second.getId(), roomService.recommendRooms(start, end).get(0).getId());
    assertTrue(
        roomService.recommendRooms(start, end).stream()
            .noneMatch(r -> r.getId().equals(first.getId())));
    ResponseStatusException conflict =
        assertThrows(
            ResponseStatusException.class,
            () -> locks.lockRoom(first.getId(), 9002L, start.plusDays(1), end));
    assertEquals(409, conflict.getStatusCode().value());
    assertDoesNotThrow(() -> locks.lockRoom(first.getId(), 9003L, end, end.plusDays(1)));
    assertEquals(
        409,
        assertThrows(ResponseStatusException.class, () -> locks.unlockRoom(second.getId(), 9001L))
            .getStatusCode()
            .value());
    assertEquals(first.getId(), locks.getLockByBookingId(9001L).getRoomId());
    locks.unlockRoom(first.getId(), 9001L);
    locks.unlockRoom(9001L);
    assertEquals(
        409,
        assertThrows(
                ResponseStatusException.class,
                () -> locks.lockRoom(first.getId(), 9001L, start, end))
            .getStatusCode()
            .value());
    locks.unlockRoom(9010L);
    assertThrows(
        ResponseStatusException.class, () -> locks.lockRoom(second.getId(), 9010L, start, end));
    assertTrue(
        roomService.recommendRooms(start, end).stream()
            .anyMatch(r -> r.getId().equals(first.getId())));
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    var ready = new java.util.concurrent.CountDownLatch(2);
    var go = new java.util.concurrent.CountDownLatch(1);
    try {
      java.util.List<java.util.concurrent.Future<Boolean>> results = new java.util.ArrayList<>();
      for (long id : new long[] {9020L, 9021L}) {
        results.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  if (!go.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    throw new AssertionError("Start timeout");
                  try {
                    locks.lockRoom(second.getId(), id, start, end);
                    return true;
                  } catch (ResponseStatusException e) {
                    assertEquals(409, e.getStatusCode().value());
                    return false;
                  }
                }));
      }
      assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
      go.countDown();
      int successes = 0;
      for (var result : results)
        if (result.get(10, java.util.concurrent.TimeUnit.SECONDS)) successes++;
      assertEquals(1, successes);
    } finally {
      go.countDown();
      executor.shutdownNow();
    }
  }
}
