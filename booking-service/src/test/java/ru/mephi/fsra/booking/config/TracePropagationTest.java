package ru.mephi.fsra.booking.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.*;

class TracePropagationTest {
  @Test
  void traceIsReturnedForwardedAndRemovedFromThread() throws Exception {
    var request = new MockHttpServletRequest("GET", "/test");
    request.addHeader("X-Trace-Id", "test-trace-123");
    var response = new MockHttpServletResponse();
    MDC.remove("traceId");
    new TraceFilter()
        .doFilter(
            request,
            response,
            (req, res) -> {
              assertEquals("test-trace-123", MDC.get("traceId"));
              var template = new feign.RequestTemplate();
              new HotelClientConfig()
                  .hotelAuthorization("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
                  .apply(template);
              assertTrue(template.headers().get("X-Trace-Id").contains("test-trace-123"));
            });
    assertEquals("test-trace-123", response.getHeader("X-Trace-Id"));
    assertNull(MDC.get("traceId"));
  }

  @Test
  void traceIsCleanedEvenWhenRequestFails() {
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();
    assertThrows(
        jakarta.servlet.ServletException.class,
        () ->
            new TraceFilter()
                .doFilter(
                    request,
                    response,
                    (req, res) -> {
                      throw new jakarta.servlet.ServletException("test");
                    }));
    assertNotNull(response.getHeader("X-Trace-Id"));
    assertNull(MDC.get("traceId"));
  }
}
