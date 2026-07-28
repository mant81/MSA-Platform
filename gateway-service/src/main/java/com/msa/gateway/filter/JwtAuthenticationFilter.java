package com.msa.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Set<String> OPEN_PATH_PREFIXES = Set.of(
            "/auth/login",
            "/auth/signup",
            "/actuator",
            "/error"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (isOpenPath(path)) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse()
                    .bufferFactory()
                    .wrap("{\"success\":false,\"code\":\"JWT_REQUIRED\",\"message\":\"Authorization header is required\"}".getBytes(StandardCharsets.UTF_8))));
        }

        String token = authorization.substring(7);
        if (!token.startsWith("jwt-access-token:")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse()
                    .bufferFactory()
                    .wrap("{\"success\":false,\"code\":\"JWT_INVALID\",\"message\":\"Invalid JWT token\"}".getBytes(StandardCharsets.UTF_8))));
        }

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .build();
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private boolean isOpenPath(String path) {
        return OPEN_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    public int getOrder() {
        return -2;
    }
}
