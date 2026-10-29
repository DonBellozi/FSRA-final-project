package ru.mephi.fsra.hotel.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class TraceFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String incoming = request.getHeader("X-Trace-Id");
    String traceId =
        incoming != null && incoming.matches("[A-Za-z0-9_-]{1,64}")
            ? incoming
            : UUID.randomUUID().toString();
    String previous = MDC.get("traceId");
    MDC.put("traceId", traceId);
    response.setHeader("X-Trace-Id", traceId);
    try {
      chain.doFilter(request, response);
    } finally {
      LoggerFactory.getLogger(getClass())
          .info(
              "traceId={} method={} path={} status={}",
              traceId,
              request.getMethod(),
              request.getRequestURI(),
              response.getStatus());
      if (previous == null) MDC.remove("traceId");
      else MDC.put("traceId", previous);
    }
  }
}
