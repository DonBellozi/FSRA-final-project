package ru.mephi.fsra.booking.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import ru.mephi.fsra.booking.repository.UserRepository;

@Component
public class JwtUserValidator implements OAuth2TokenValidator<Jwt> {

  private final UserRepository userRepository;

  public JwtUserValidator(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    Number userId = jwt.getClaim("userId");
    String username = jwt.getSubject();
    String role = jwt.getClaimAsString("role");

    if (userId == null || username == null || role == null) {
      return invalidToken();
    }

    return userRepository
        .findById(userId.longValue())
        .filter(user -> username.equals(user.getUsername()))
        .filter(user -> role.equals(user.getRole().name()))
        .map(user -> OAuth2TokenValidatorResult.success())
        .orElseGet(this::invalidToken);
  }

  private OAuth2TokenValidatorResult invalidToken() {
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error("invalid_token", "JWT user does not match the current database", null));
  }
}
