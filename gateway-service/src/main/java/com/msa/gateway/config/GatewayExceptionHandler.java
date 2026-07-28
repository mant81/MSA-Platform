package com.msa.gateway.config;

import com.msa.core.BaseResponse;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Order(-2)
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        BaseResponse body = BaseResponse.builder()
                .timestamp(OffsetDateTime.now().toString())
                .traceId(traceId)
                .path(exchange.getRequest().getPath().value())
                .success(false)
                .code(status.name())
                .message(ex.getMessage() == null ? "Gateway error" : ex.getMessage())
                .build();

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set("X-Trace-Id", traceId);

        String json = String.format(
                "{\"timestamp\":\"%s\",\"traceId\":\"%s\",\"path\":\"%s\",\"success\":%s,\"code\":\"%s\",\"message\":\"%s\"}",
                body.getTimestamp(),
                body.getTraceId(),
                body.getPath(),
                body.isSuccess(),
                body.getCode(),
                body.getMessage().replace("\"", "\\\"")
        );

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
