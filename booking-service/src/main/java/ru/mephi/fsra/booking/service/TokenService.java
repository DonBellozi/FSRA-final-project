package ru.mephi.fsra.booking.service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import ru.mephi.fsra.booking.entity.User;

@Service
public class TokenService {
  private final JwtEncoder encoder;

  public TokenService(@Value("${app.jwt.secret}") String secret) {
    encoder = new NimbusJwtEncoder(new ImmutableSecret<>(Base64.getDecoder().decode(secret)));
  }

  public record TokenResponse(
      Long id,
      String username,
      String role,
      String accessToken,
      String tokenType,
      long expiresIn,
      Long userId) {}

  public TokenResponse issue(User user) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("fsra-final-project")
            .subject(user.getUsername())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(3600))
            .claim("userId", user.getId())
            .claim("role", user.getRole().name())
            .build();
    String token =
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    return new TokenResponse(
        user.getId(),
        user.getUsername(),
        user.getRole().name(),
        token,
        "Bearer",
        3600,
        user.getId());
  }
}
