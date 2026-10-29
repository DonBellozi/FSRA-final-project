package ru.mephi.fsra.hotel.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mephi.fsra.hotel.entity.CancelledReservation;

public interface CancelledReservationRepository extends JpaRepository<CancelledReservation, Long> {}
