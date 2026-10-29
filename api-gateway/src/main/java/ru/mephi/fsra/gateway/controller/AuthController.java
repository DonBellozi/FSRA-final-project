package ru.mephi.fsra.gateway.controller;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final JwtEncoder jwtEncoder;
  private final WebClient authClient;

  public AuthController(JwtEncoder jwtEncoder, @LoadBalanced WebClient.Builder webClientBuilder) {
    this.jwtEncoder = jwtEncoder;
    this.authClient = webClientBuilder.baseUrl("http://booking-service").build();
  }

  @PostMapping("/login")
  public Mono<ResponseEntity<?>> login(
      @RequestBody LoginRequest request,
      @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
    if (request.username() == null || request.password() == null) {
      return Mono.just(unauthorized());
    }

    return authClient
        .post()
        .uri("/api/users/auth")
        .header("X-Trace-Id", traceId == null ? java.util.UUID.randomUUID().toString() : traceId)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .retrieve()
        .onStatus(
            status -> status.value() == 401,
            response -> Mono.error(new InvalidCredentialsException()))
        .bodyToMono(AuthUser.class)
        .map(this::createToken)
        .onErrorResume(InvalidCredentialsException.class, error -> Mono.just(unauthorized()))
        .onErrorResume(
            Exception.class,
            error -> {
              org.slf4j.LoggerFactory.getLogger(getClass())
                  .warn("Authentication service unavailable", error);

              return Mono.just(
                  ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                      .body(Map.of("error", "Authentication service unavailable")));
            });
  }

  private ResponseEntity<?> createToken(AuthUser user) {
    Instant now = Instant.now();

    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("fsra-final-project")
            .subject(user.username())
            .issuedAt(now)
            .expiresAt(now.plus(1, ChronoUnit.HOURS))
            .claim("userId", user.id())
            .claim("role", user.role())
            .build();

    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

    String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

    return ResponseEntity.ok(
        Map.of(
            "accessToken",
            token,
            "tokenType",
            "Bearer",
            "expiresIn",
            3600,
            "userId",
            user.id(),
            "role",
            user.role()));
  }

  private ResponseEntity<?> unauthorized() {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(Map.of("error", "Invalid credentials"));
  }

  public record LoginRequest(String username, String password) {}

  public record AuthUser(Long id, String username, String role) {}

  private static class InvalidCredentialsException extends RuntimeException {}
}
