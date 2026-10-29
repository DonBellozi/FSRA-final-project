package ru.mephi.fsra.booking.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import ru.mephi.fsra.booking.entity.User;
import ru.mephi.fsra.booking.entity.UserRole;
import ru.mephi.fsra.booking.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class JwtUserValidatorTest {

  @Mock private UserRepository userRepository;

  private JwtUserValidator validator;

  @BeforeEach
  void setUp() {
    validator = new JwtUserValidator(userRepository);
  }

  private Jwt createJwt(Long userId, String username, String role) {
    return Jwt.withTokenValue("test-token")
        .header("alg", "HS256")
        .subject(username)
        .claim("userId", userId)
        .claim("role", role)
        .build();
  }

  @Test
  void validUser_shouldAcceptJwt() {
    User user = new User();
    user.setUsername("persistence-test");
    user.setRole(UserRole.USER);

    when(userRepository.findById(2L)).thenReturn(Optional.of(user));

    Jwt jwt = createJwt(2L, "persistence-test", "USER");

    assertFalse(validator.validate(jwt).hasErrors());
  }

  @Test
  void wrongUsername_shouldRejectJwt() {
    User user = new User();
    user.setUsername("persistence-test");
    user.setRole(UserRole.USER);

    when(userRepository.findById(2L)).thenReturn(Optional.of(user));

    Jwt jwt = createJwt(2L, "user103", "USER");

    assertTrue(validator.validate(jwt).hasErrors());
  }

  @Test
  void missingUser_shouldRejectJwt() {
    when(userRepository.findById(999L)).thenReturn(Optional.empty());

    Jwt jwt = createJwt(999L, "deleted-user", "USER");

    assertTrue(validator.validate(jwt).hasErrors());
  }

  @Test
  void wrongRole_shouldRejectJwt() {
    User user = new User();
    user.setUsername("persistence-test");
    user.setRole(UserRole.USER);

    when(userRepository.findById(2L)).thenReturn(Optional.of(user));

    Jwt jwt = createJwt(2L, "persistence-test", "ADMIN");

    assertTrue(validator.validate(jwt).hasErrors());
  }
}
