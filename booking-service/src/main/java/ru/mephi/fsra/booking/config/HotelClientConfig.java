package ru.mephi.fsra.booking.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import feign.RequestInterceptor;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

@Configuration
public class HotelClientConfig {
  @Bean
  public HotelCircuitBreakerCapability hotelCircuitBreaker() {
    return new HotelCircuitBreakerCapability();
  }

  @Bean
  RequestInterceptor hotelAuthorization(@Value("${app.jwt.secret}") String secret) {
    JwtEncoder encoder =
        new NimbusJwtEncoder(new ImmutableSecret<>(Base64.getDecoder().decode(secret)));
    return template -> {
      Instant now = Instant.now();
      JwtClaimsSet claims =
          JwtClaimsSet.builder()
              .subject("booking-service")
              .issuedAt(now)
              .expiresAt(now.plusSeconds(3600))
              .claim("role", "INTERNAL")
              .build();
      String token =
          encoder
              .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
              .getTokenValue();
      template.header("Authorization", "Bearer " + token);
      String traceId = org.slf4j.MDC.get("traceId");
      template.header(
          "X-Trace-Id", traceId == null ? java.util.UUID.randomUUID().toString() : traceId);
    };
  }
}
