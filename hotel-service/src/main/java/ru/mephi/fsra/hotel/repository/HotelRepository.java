package ru.mephi.fsra.hotel.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mephi.fsra.hotel.entity.Hotel;

public interface HotelRepository extends JpaRepository<Hotel, Long> {}
