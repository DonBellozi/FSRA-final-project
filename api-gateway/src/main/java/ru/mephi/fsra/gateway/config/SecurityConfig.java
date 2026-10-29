package ru.mephi.fsra.gateway.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

  @Bean
  public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {

    JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();

    jwtConverter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          String role = jwt.getClaimAsString("role");

          if (role == null) {
            return List.of();
          }

          return List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });

    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .cors(cors -> {})
        .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .authorizeExchange(
            exchange ->
                exchange

                    // Проверка состояния Gateway
                    .pathMatchers("/actuator/health")
                    .permitAll()
                    .pathMatchers(
                        "/api/rooms/*/confirm-availability",
                        "/api/rooms/*/release",
                        "/api/room-locks/**")
                    .denyAll()

                    // Получение JWT
                    .pathMatchers("/api/auth/**")
                    .permitAll()

                    // Публичная саморегистрация
                    .pathMatchers(HttpMethod.POST, "/api/users/register", "/api/users/auth")
                    .permitAll()

                    // Публичное чтение отелей и номеров
                    .pathMatchers(
                        HttpMethod.GET,
                        "/api/hotels",
                        "/api/hotels/**",
                        "/api/rooms",
                        "/api/rooms/**")
                    .permitAll()

                    // Управление отелями и номерами – ADMIN
                    .pathMatchers("/api/hotels/**", "/api/rooms/**")
                    .hasRole("ADMIN")

                    // История бронирований – USER или ADMIN
                    // Проверку владельца выполняет booking-service
                    .pathMatchers(HttpMethod.GET, "/api/bookings/user/*")
                    .hasAnyRole("USER", "ADMIN")

                    // Общая статистика – ADMIN
                    .pathMatchers(HttpMethod.GET, "/api/bookings/stats")
                    .hasRole("ADMIN")

                    // Рекомендации – USER или ADMIN
                    // Проверку владельца выполняет booking-service
                    .pathMatchers(HttpMethod.GET, "/api/bookings/recommendations/*")
                    .hasAnyRole("USER", "ADMIN")

                    // Остальные операции бронирования – USER или ADMIN
                    .pathMatchers("/api/bookings/**")
                    .hasAnyRole("USER", "ADMIN")

                    // Остальные запросы требуют авторизации
                    .anyExchange()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(
                    jwt ->
                        jwt.jwtAuthenticationConverter(
                            new ReactiveJwtAuthenticationConverterAdapter(jwtConverter))))
        .build();
  }

  // CORS для Swagger UI
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {

    CorsConfiguration config = new CorsConfiguration();

    // Разрешенные источники
    config.setAllowedOrigins(List.of("http://localhost:8081", "http://localhost:8082"));

    // Разрешенные HTTP-методы
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

    // Разрешенные заголовки
    config.setExposedHeaders(List.of("X-Trace-Id"));
    config.setAllowedHeaders(
        List.of("Authorization", "Content-Type", "Accept", "Idempotency-Key", "X-Trace-Id"));

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

    source.registerCorsConfiguration("/**", config);

    return source;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
