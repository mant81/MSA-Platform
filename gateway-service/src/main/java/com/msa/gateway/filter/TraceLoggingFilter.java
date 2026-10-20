package com.msa.gateway.filter;

import com.msa.core.TraceEventType;
import com.msa.core.TraceEventVo;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.WebFilter;
import com.msa.core.security.ServiceCredentials;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Component
public class TraceLoggingFilter implements WebFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(TraceLoggingFilter.class);
    private final Sinks.Many<TraceEventVo> events = Sinks.many().unicast()
            .onBackpressureBuffer(new ArrayBlockingQueue<>(256));
    private final AtomicLong droppedEvents = new AtomicLong();
    private final AtomicLong failedEvents = new AtomicLong();
    private final Disposable sender;

    public TraceLoggingFilter(@Value("${app.trace-service-url:http://localhost:8090}") String traceServiceUrl, ServiceCredentials credentials) {
        WebClient client = WebClient.builder().baseUrl(traceServiceUrl).defaultHeaders(headers -> credentials.apply(headers, "trace-service")).build();
        sender = events.asFlux().concatMap(event -> client.post().uri("/trace-events")
                .bodyValue(event).retrieve().toBodilessEntity().then()
                .timeout(Duration.ofMillis(500))
                .onErrorResume(ex -> {
                    log.warn("Gateway Trace delivery failed; total={}", failedEvents.incrementAndGet());
                    return Mono.empty();
                }), 1).subscribe();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return Mono.defer(() -> {
            long start = System.nanoTime();
            String traceId = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
            if (traceId == null || traceId.isBlank()) {
                traceId = UUID.randomUUID().toString().replace("-", "");
            }
            String id = traceId;
            exchange.getAttributes().put("traceId", id);
            String path = exchange.getRequest().getPath().value();
            String method = exchange.getRequest().getMethod().name();
            emit(event(id, path, method, "RUNNING", 0L, null));
            ServerHttpRequest request = exchange.getRequest().mutate().headers(h -> h.set("X-Trace-Id", id)).build();
            exchange.getResponse().getHeaders().set("X-Trace-Id", id);
            // Routing may copy a downstream header. Keep a single correlation value.
            exchange.getResponse().beforeCommit(() -> {
                exchange.getResponse().getHeaders().set("X-Trace-Id", id);
                return Mono.empty();
            });
            return chain.filter(exchange.mutate().request(request).build())
                    .doOnSuccess(unused -> {
                        var status = exchange.getResponse().getStatusCode();
                        boolean failed = status != null && status.value() >= 400;
                        emit(event(id, path, method, failed ? "FAILED" : "SUCCESS",
                                (System.nanoTime() - start) / 1_000_000,
                                failed ? "HTTP_" + status.value() : null));
                    })
                    .doOnError(ex -> emit(event(id, path, method, "FAILED",
                            (System.nanoTime() - start) / 1_000_000, "GATEWAY_ERROR")));
        });
    }

    private TraceEventVo event(String id, String path, String method, String status, long elapsed, String code) {
        TraceEventVo event = new TraceEventVo();
        event.setTraceId(id);
        event.setServiceName("gateway-service");
        event.setEventType("RUNNING".equals(status) ? TraceEventType.REQUEST_STARTED.name()
                : "FAILED".equals(status) ? TraceEventType.REQUEST_FAILED.name() : TraceEventType.REQUEST_COMPLETED.name());
        event.setHttpMethod(method);
        event.setPath(path);
        event.setStatus(status);
        event.setElapsedMs(elapsed);
        event.setErrorCode(code);
        event.setCreatedAt(OffsetDateTime.now());
        return event;
    }

    private synchronized void emit(TraceEventVo event) {
        if (events.tryEmitNext(event).isFailure()) {
            log.warn("Gateway Trace buffer full or unavailable; dropped={}", droppedEvents.incrementAndGet());
        }
    }

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 10; }

    @PreDestroy
    public void stop() {
        sender.dispose();
    }
}
