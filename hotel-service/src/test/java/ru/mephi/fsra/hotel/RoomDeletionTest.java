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
    properties = {"eureka.client.enabled=false", "spring.datasource.url=jdbc:h2:mem:deletion-test"})
class RoomDeletionTest {
  @Autowired HotelRepository hotels;
  @Autowired RoomRepository rooms;
  @Autowired RoomService roomService;
  @Autowired HotelService hotelService;
  @Autowired RoomLockService locks;
  @Autowired RoomStatisticsService statistics;

  @Test
  void bookedRoomCannotBeDeletedUntilReleased() {
    Hotel hotel = new Hotel();
    hotel.setName("Deletion test");
    hotel.setAddress("Test");
    hotel = hotels.saveAndFlush(hotel);
    Long hotelId = hotel.getId();
    Room room = rooms.saveAndFlush(new Room(hotel, "1"));
    Long roomId = room.getId();
    LocalDate start = LocalDate.now().plusDays(10);
    locks.lockRoom(roomId, 80001L, start, start.plusDays(2));
    var stat = statistics.statistics(start.minusDays(1), start.plusDays(3)).get(0);
    assertEquals(2, stat.occupiedNights());
    assertEquals(4, stat.periodNights());
    assertEquals(50.0, stat.occupancyPercent());
    assertEquals(1, stat.bookingsInPeriod());
    assertEquals(
        1, statistics.statistics(start.plusDays(1), start.plusDays(3)).get(0).occupiedNights());
    assertEquals(
        400,
        assertThrows(ResponseStatusException.class, () -> statistics.statistics(start, start))
            .getStatusCode()
            .value());
    assertEquals(
        409,
        assertThrows(ResponseStatusException.class, () -> roomService.deleteRoom(roomId))
            .getStatusCode()
            .value());
    assertTrue(rooms.existsById(roomId));
    assertEquals(
        409,
        assertThrows(ResponseStatusException.class, () -> hotelService.deleteHotel(hotelId))
            .getStatusCode()
            .value());
    locks.unlockRoom(80001L);
    assertEquals(0, statistics.statistics(start, start.plusDays(2)).get(0).occupiedNights());
    roomService.deleteRoom(roomId);
    assertFalse(rooms.existsById(roomId));
    hotelService.deleteHotel(hotelId);
    assertFalse(hotels.existsById(hotelId));
  }
}
