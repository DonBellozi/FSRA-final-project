package ru.mephi.fsra.booking.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.mephi.fsra.booking.entity.UserRole;
import ru.mephi.fsra.booking.repository.UserRepository;
import ru.mephi.fsra.booking.service.UserService;

@SpringBootTest(
    properties = {
      "eureka.client.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:admin-initializer-test;DB_CLOSE_DELAY=-1"
    })
class AdminInitializerTest {
  @Autowired UserService users;
  @Autowired UserRepository repository;
  @Autowired AdminInitializer initializer;

  @Test
  void defaultAdminCanLoginAndRepeatedInitializationDoesNotDuplicateIt() {
    var user = users.authenticate("admin", "StudyAdmin123!");
    assertEquals(UserRole.ADMIN, user.getRole());
    assertNotEquals("StudyAdmin123!", user.getPassword());
    long before = repository.count();
    initializer.run();
    assertEquals(before, repository.count());
  }
}
