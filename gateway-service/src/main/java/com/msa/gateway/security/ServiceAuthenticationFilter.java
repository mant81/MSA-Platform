package com.msa.gateway.security;

import com.msa.core.security.ServiceCredentials;
import org.springframework.cloud.gateway.route.Route;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class ServiceAuthenticationFilter implements GlobalFilter, Ordered {
    private final ServiceCredentials credentials;
    public ServiceAuthenticationFilter(ServiceCredentials credentials) { this.credentials = credentials; }
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getRequiredAttribute(GATEWAY_ROUTE_ATTR);
        var request = exchange.getRequest().mutate().headers(headers -> credentials.apply(headers, route.getId())).build();
        return chain.filter(exchange.mutate().request(request).build());
    }
    @Override
    public int getOrder() { return -1; }
}
