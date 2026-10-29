package ru.mephi.fsra.booking.config;

import static org.junit.jupiter.api.Assertions.*;

import feign.*;
import io.github.resilience4j.circuitbreaker.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HotelCircuitBreakerTest {
  private Request request(String path) {
    return Request.create(
        Request.HttpMethod.POST,
        "http://hotel" + path,
        Map.of(),
        null,
        StandardCharsets.UTF_8,
        null);
  }

  @Test
  void opensSkipsCallsAndRecoversWithoutBlockingCompensation() throws Exception {
    var config =
        CircuitBreakerConfig.from(HotelCircuitBreakerCapability.configuration())
            .slidingWindowSize(2)
            .minimumNumberOfCalls(2)
            .permittedNumberOfCallsInHalfOpenState(1)
            .build();
    var confirm = CircuitBreaker.of("test-confirm", config);
    var release = CircuitBreaker.of("test-release", config);
    AtomicInteger calls = new AtomicInteger();
    Client delegate =
        (req, options) -> {
          int number = calls.incrementAndGet();
          return Response.builder()
              .request(req)
              .status(number <= 2 ? 500 : 200)
              .headers(Map.of())
              .build();
        };
    Client client = new HotelCircuitBreakerCapability(confirm, release).enrich(delegate);
    var request = request("/api/rooms/1/confirm-availability");
    client.execute(request, new Request.Options());
    client.execute(request, new Request.Options());
    assertEquals(CircuitBreaker.State.OPEN, confirm.getState());
    assertEquals(503, client.execute(request, new Request.Options()).status());
    assertEquals(2, calls.get());
    assertEquals(
        200, client.execute(request("/api/rooms/1/release"), new Request.Options()).status());
    confirm.transitionToHalfOpenState();
    assertEquals(200, client.execute(request, new Request.Options()).status());
    assertEquals(CircuitBreaker.State.CLOSED, confirm.getState());
  }

  @Test
  void conflictsDoNotTripCircuit() throws Exception {
    var circuit = CircuitBreaker.of("conflicts", HotelCircuitBreakerCapability.configuration());
    Client client =
        new HotelCircuitBreakerCapability(circuit, circuit)
            .enrich(
                (Client)
                    (req, options) ->
                        Response.builder().request(req).status(409).headers(Map.of()).build());
    for (int i = 0; i < 10; i++) client.execute(request("/confirm"), new Request.Options());
    assertEquals(CircuitBreaker.State.CLOSED, circuit.getState());
  }
}
