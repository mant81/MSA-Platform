package com.msa.core.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@Import(JwtSecurityConfiguration.class)
public class ServletSecurityConfiguration {
    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http, ServiceCredentials credentials,
            JwtDecoder decoder, JwtAuthenticationConverter converter) throws Exception {
        http.csrf(csrf -> csrf.disable()).sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(login -> login.disable()).httpBasic(basic -> basic.disable())
                .addFilterBefore(new ServiceCredentialFilter(credentials), BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> {
                    auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                            .requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                    if ("auth-service".equals(credentials.id())) {
                        auth.requestMatchers(HttpMethod.POST, "/auth/signup", "/auth/login", "/auth/refresh", "/auth/logout").permitAll()
                                .requestMatchers(HttpMethod.GET, "/users").hasRole("ADMIN");
                    } else if ("trace-service".equals(credentials.id())) {
                        auth.requestMatchers(HttpMethod.POST, "/trace-events").permitAll()
                                .requestMatchers(HttpMethod.GET, "/trace-events", "/trace-events/**").hasAnyRole("OPERATIONS", "ADMIN");
                    } else if ("config-service".equals(credentials.id())) {
                        auth.requestMatchers(HttpMethod.POST, "/configs", "/configs/**").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.GET, "/configs", "/configs/**").hasAnyRole("USER", "OPERATIONS", "ADMIN");
                    } else if ("member-service".equals(credentials.id())) {
                        auth.requestMatchers("/members", "/members/**").hasAnyRole("USER", "ADMIN");
                    } else if ("hr-service".equals(credentials.id())) {
                        auth.requestMatchers(HttpMethod.GET, "/hr/employees", "/hr/employees/**").hasAnyRole("USER", "ADMIN");
                    }
                    auth.anyRequest().denyAll();
                }).oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter)));
        return http.build();
    }
}
