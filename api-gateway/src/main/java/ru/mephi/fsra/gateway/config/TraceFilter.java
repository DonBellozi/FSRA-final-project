package ru.mephi.fsra.gateway.config;

import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

@Component
@Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class TraceFilter implements WebFilter {
  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String incoming = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
    String traceId =
        incoming != null && incoming.matches("[A-Za-z0-9_-]{1,64}")
            ? incoming
            : UUID.randomUUID().toString();
    var traced =
        exchange
            .mutate()
            .request(request -> request.headers(headers -> headers.set("X-Trace-Id", traceId)))
            .build();
    exchange.getResponse().getHeaders().set("X-Trace-Id", traceId);
    return chain
        .filter(traced)
        .doFinally(
            signal ->
                org.slf4j.LoggerFactory.getLogger(getClass())
                    .info(
                        "traceId={} method={} path={} status={}",
                        traceId,
                        exchange.getRequest().getMethod(),
                        exchange.getRequest().getPath(),
                        exchange.getResponse().getStatusCode()));
  }
}
