package ru.mephi.fsra.booking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import ru.mephi.fsra.booking.entity.User;
import ru.mephi.fsra.booking.entity.UserRole;
import ru.mephi.fsra.booking.repository.UserRepository;

@Component
public class AdminInitializer implements CommandLineRunner {

  private final UserRepository userRepository;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

  @Value("${app.admin.username}")
  private String adminUsername;

  @Value("${app.admin.password}")
  private String adminPassword;

  public AdminInitializer(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public void run(String... args) {
    if (adminUsername.isBlank() || adminPassword.isBlank()) {
      return;
    }

    if (adminPassword.length() < 12) {
      throw new IllegalStateException("Admin password must contain at least 12 characters");
    }

    if (userRepository.existsByUsername(adminUsername)) {
      return;
    }

    User admin = new User();
    admin.setUsername(adminUsername);
    admin.setPassword(encoder.encode(adminPassword));
    admin.setRole(UserRole.ADMIN);

    userRepository.save(admin);

    org.slf4j.LoggerFactory.getLogger(getClass())
        .info("Initial admin account created: {}", adminUsername);
  }
}
