package com.msa.gateway.filter;

import com.msa.core.TraceEventType;
import com.msa.core.TraceEventVo;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class TraceLoggingFilter implements GlobalFilter, Ordered {

    private final WebClient webClient;

    public TraceLoggingFilter(@Value("${app.trace-service-url:http://localhost:8090}") String traceServiceUrl) {
        this.webClient = WebClient.builder().baseUrl(traceServiceUrl).build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start = System.currentTimeMillis();
        String traceId = getOrCreateTraceId(exchange.getRequest());
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethodValue();

        TraceEventVo started = new TraceEventVo();
        started.setTraceId(traceId);
        started.setEventType(TraceEventType.REQUEST_STARTED.name());
        started.setHttpMethod(method);
        started.setPath(path);
        started.setStatus("RUNNING");
        started.setCreatedAt(OffsetDateTime.now());
        postTrace(started);

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-Trace-Id", traceId)
                .build();
        ServerHttpResponse mutatedResponse = exchange.getResponse();
        mutatedResponse.getHeaders().add("X-Trace-Id", traceId);

        return chain.filter(exchange.mutate().request(mutatedRequest).response(mutatedResponse).build())
                .doOnSuccess(v -> saveCompleted(traceId, path, method, start, "SUCCESS", null, null))
                .doOnError(ex -> saveCompleted(traceId, path, method, start, "FAILED", "GATEWAY_ERROR", ex.getMessage()));
    }

    private void saveCompleted(String traceId, String path, String method, long start, String status, String errorCode, String errorMessage) {
        TraceEventVo event = new TraceEventVo();
        event.setTraceId(traceId);
        event.setEventType("SUCCESS".equals(status) ? TraceEventType.REQUEST_COMPLETED.name() : TraceEventType.REQUEST_FAILED.name());
        event.setHttpMethod(method);
        event.setPath(path);
        event.setStatus(status);
        event.setElapsedMs(System.currentTimeMillis() - start);
        event.setErrorCode(errorCode);
        event.setErrorMessage(errorMessage);
        event.setCreatedAt(OffsetDateTime.now());
        postTrace(event);
    }

    private String getOrCreateTraceId(ServerHttpRequest request) {
        String traceId = request.getHeaders().getFirst("X-Trace-Id");
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().replace("-", "").substring(0, 16)
                : traceId;
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private void postTrace(TraceEventVo vo) {
        webClient.post()
                .uri("/trace-events")
                .bodyValue(vo)
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}
