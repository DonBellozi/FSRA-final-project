package ru.mephi.fsra.booking.config;

import feign.*;
import io.github.resilience4j.circuitbreaker.*;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/** Confirmation and compensation have independent circuits. */
public class HotelCircuitBreakerCapability implements Capability {
  private final CircuitBreaker confirmation;
  private final CircuitBreaker release;

  public HotelCircuitBreakerCapability() {
    this(
        CircuitBreaker.of("hotel-confirmation", configuration()),
        CircuitBreaker.of("hotel-release", configuration()));
  }

  public HotelCircuitBreakerCapability(CircuitBreaker confirmation, CircuitBreaker release) {
    this.confirmation = confirmation;
    this.release = release;
  }

  public static CircuitBreakerConfig configuration() {
    return CircuitBreakerConfig.custom()
        .slidingWindowSize(6)
        .minimumNumberOfCalls(6)
        .failureRateThreshold(50)
        .waitDurationInOpenState(Duration.ofSeconds(10))
        .permittedNumberOfCallsInHalfOpenState(2)
        .recordResult(result -> result instanceof Response response && response.status() >= 500)
        .build();
  }

  @Override
  public Client enrich(Client delegate) {
    return (request, options) -> {
      CircuitBreaker circuit = request.url().contains("/release") ? release : confirmation;
      try {
        return circuit.executeCallable(() -> delegate.execute(request, options));
      } catch (CallNotPermittedException e) {
        return Response.builder()
            .request(request)
            .status(503)
            .reason("Hotel circuit is open")
            .headers(Map.of())
            .build();
      } catch (IOException e) {
        throw e;
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new IOException("Hotel call failed", e);
      }
    };
  }
}
