package com.msa.gateway.security;

import com.msa.core.security.JwtSecurityConfiguration;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Configuration(proxyBeanMethods = false)
@Import(JwtSecurityConfiguration.class)
public class GatewaySecurityConfiguration {
  @Bean
  public SecurityWebFilterChain gatewaySecurity(
      ServerHttpSecurity http, JwtDecoder decoder, JwtAuthenticationConverter converter) {
    return http.csrf(csrf -> csrf.disable())
        .formLogin(login -> login.disable())
        .httpBasic(basic -> basic.disable())
        .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
        .authorizeExchange(
            auth ->
                auth.pathMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .pathMatchers(
                        HttpMethod.POST,
                        "/auth/signup",
                        "/auth/login",
                        "/auth/refresh",
                        "/auth/logout")
                    .permitAll()
                    .pathMatchers(HttpMethod.POST, "/operations/services/*/maintenance")
                    .hasRole("ADMIN")
                    .pathMatchers(HttpMethod.POST, "/operations/incidents/*/ack")
                    .hasAnyRole("OPERATIONS", "ADMIN")
                    .pathMatchers(HttpMethod.GET, "/actuator/prometheus")
                    .hasAnyRole("OPERATIONS", "ADMIN")
                    .pathMatchers(HttpMethod.GET, "/users")
                    .hasRole("ADMIN")
                    .pathMatchers(
                        HttpMethod.GET, "/trace-events", "/trace-events/**", "/operations/**")
                    .hasAnyRole("OPERATIONS", "ADMIN")
                    .pathMatchers(HttpMethod.GET, "/configs", "/configs/**")
                    .hasAnyRole("USER", "OPERATIONS", "ADMIN")
                    .pathMatchers(HttpMethod.POST, "/configs", "/configs/**")
                    .hasRole("ADMIN")
                    .pathMatchers(HttpMethod.GET, "/members/processes", "/members/processes/**")
                    .hasAnyRole("OPERATIONS", "ADMIN")
                    .pathMatchers(HttpMethod.POST, "/members")
                    .hasRole("ADMIN")
                    .pathMatchers("/members", "/members/**", "/hr/employees", "/hr/employees/**")
                    .hasAnyRole("USER", "ADMIN")
                    .anyExchange()
                    .denyAll())
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint((exchange, error) -> reject(exchange, 401))
                    .accessDeniedHandler((exchange, error) -> reject(exchange, 403)))
        .oauth2ResourceServer(
            resource ->
                resource
                    .authenticationEntryPoint((exchange, error) -> reject(exchange, 401))
                    .accessDeniedHandler((exchange, error) -> reject(exchange, 403))
                    .jwt(
                        jwt ->
                            jwt.jwtDecoder(
                                    token ->
                                        Mono.fromCallable(() -> decoder.decode(token))
                                            .subscribeOn(Schedulers.boundedElastic()))
                                .jwtAuthenticationConverter(
                                    new ReactiveJwtAuthenticationConverterAdapter(converter))))
        .build();
  }

  private Mono<Void> reject(ServerWebExchange exchange, int status) {
    exchange.getResponse().setStatusCode(HttpStatus.valueOf(status));
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
    if (status == 401) {
      exchange.getResponse().getHeaders().set("WWW-Authenticate", "Bearer");
    }
    byte[] body =
        ("{\"success\":false,\"code\":\""
                + (status == 401 ? "AUTH_REQUIRED" : "ACCESS_DENIED")
                + "\"}")
            .getBytes(StandardCharsets.UTF_8);
    return exchange
        .getResponse()
        .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
  }
}
