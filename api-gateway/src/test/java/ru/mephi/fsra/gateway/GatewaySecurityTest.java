package ru.mephi.fsra.gateway;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureWebTestClient
@org.springframework.context.annotation.Import(GatewaySecurityTest.ProbeConfiguration.class)
class GatewaySecurityTest {

  private WebTestClient webTestClient;

  @Autowired private org.springframework.context.ApplicationContext context;

  @org.junit.jupiter.api.BeforeEach
  void bindWithoutNetwork() {
    webTestClient =
        WebTestClient.bindToApplicationContext(context)
            .apply(
                org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers
                    .springSecurity())
            .configureClient()
            .build();
  }

  @org.springframework.boot.test.context.TestConfiguration
  static class ProbeConfiguration {
    @org.springframework.context.annotation.Bean
    ProbeController probeController() {
      return new ProbeController();
    }
  }

  @org.springframework.web.bind.annotation.RestController
  static class ProbeController {
    @org.springframework.web.bind.annotation.GetMapping("/api/bookings/stats")
    java.util.Map<String, Integer> stats() {
      return java.util.Map.of("totalBookings", 0);
    }
  }

  @Autowired private JwtEncoder jwtEncoder;

  @Test
  void traceIdIsReturnedEvenWhenAuthorizationFails() {
    webTestClient
        .get()
        .uri("/api/bookings/stats")
        .header("X-Trace-Id", "gateway-test-123")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .valueEquals("X-Trace-Id", "gateway-test-123");
  }

  @Test
  void internalRoutesAreDeniedEvenForAdmin() {
    for (String path : new String[] {"/api/rooms/7/release", "/api/rooms/7/confirm-availability"}) {
      webTestClient
          .mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
          .post()
          .uri(path)
          .exchange()
          .expectStatus()
          .isForbidden();
    }
  }

  // Тест 1. Без JWT возвращается 401
  @Test
  void getBookingStats_shouldReturn401_whenTokenMissing() {
    webTestClient.get().uri("/api/bookings/stats").exchange().expectStatus().isUnauthorized();
  }

  // Тест 2. USER не имеет доступа к статистике
  @Test
  void getBookingStats_shouldReturn403_whenUserRole() {
    webTestClient
        .mutateWith(
            mockJwt()
                .jwt(jwt -> jwt.claim("role", "USER"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
        .get()
        .uri("/api/bookings/stats")
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  // Тест 3. ADMIN проходит проверку авторизации
  @Test
  void getBookingStats_shouldAllowAdminRole() {
    Instant now = Instant.now();

    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject("test-admin")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(3600))
            .claim("role", "ADMIN")
            .build();

    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

    String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

    webTestClient
        .get()
        .uri("/api/bookings/stats")
        .headers(headers -> headers.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.totalBookings")
        .isEqualTo(0);
  }

  // Тест 4. Реальный JWT HS256 с ролью USER
  @Test
  void getBookingStats_shouldReturn403_withRealUserJwt() {
    Instant now = Instant.now();

    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject("test-user")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(3600))
            .claim("role", "USER")
            .build();

    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

    String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

    webTestClient
        .get()
        .uri("/api/bookings/stats")
        .headers(headers -> headers.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isForbidden();
  }
}
