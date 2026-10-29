package ru.mephi.fsra.hotel.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mephi.fsra.hotel.entity.Room;

public interface RoomRepository extends JpaRepository<Room, Long> {

  List<Room> findByAvailableTrue();

  boolean existsByHotelId(Long hotelId);

  List<Room> findByHotelId(Long hotelId);

  List<Room> findByAvailableTrueOrderByTimesBookedAscIdAsc();

  @Query(
      "SELECT r FROM Room r WHERE r.available = true AND NOT EXISTS (SELECT l FROM RoomLock l WHERE l.roomId = r.id AND l.checkIn < :checkOut AND l.checkOut > :checkIn) ORDER BY r.timesBooked ASC, r.id ASC")
  List<Room> findFreeRooms(
      @Param("checkIn") java.time.LocalDate checkIn,
      @Param("checkOut") java.time.LocalDate checkOut);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT r FROM Room r WHERE r.id = :id")
  Optional<Room> findByIdForUpdate(@Param("id") Long id);
}
