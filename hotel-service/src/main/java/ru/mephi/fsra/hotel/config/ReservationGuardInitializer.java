package ru.mephi.fsra.hotel.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import ru.mephi.fsra.hotel.entity.ReservationGuard;
import ru.mephi.fsra.hotel.repository.ReservationGuardRepository;

@Component
public class ReservationGuardInitializer implements CommandLineRunner {
  private final ReservationGuardRepository repository;

  public ReservationGuardInitializer(ReservationGuardRepository repository) {
    this.repository = repository;
  }

  public void run(String... args) {
    if (!repository.existsById(1L)) repository.saveAndFlush(new ReservationGuard(1L));
  }
}
