package ru.mephi.fsra.hotel.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import ru.mephi.fsra.hotel.entity.ReservationGuard;

public interface ReservationGuardRepository extends JpaRepository<ReservationGuard, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select g from ReservationGuard g where g.id = 1")
  ReservationGuard acquire();
}
