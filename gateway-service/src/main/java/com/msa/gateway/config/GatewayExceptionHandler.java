package com.msa.gateway.config;

import com.msa.core.BaseResponse;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

@Component
@Order(-2)
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) { return Mono.error(ex); }
        HttpStatusCode status = ex instanceof ResponseStatusException response
                ? response.getStatusCode() : HttpStatus.INTERNAL_SERVER_ERROR;
        String traceId = exchange.getAttribute("traceId");
        if (traceId == null) { traceId = UUID.randomUUID().toString().replace("-", ""); }
        BaseResponse body = new BaseResponse(OffsetDateTime.now().toString(), traceId,
                exchange.getRequest().getPath().value(), false, "HTTP_" + status.value(), "Gateway request failed");
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set("X-Trace-Id", traceId);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse()
                .bufferFactory().wrap(mapper.writeValueAsBytes(body))));
    }
}
