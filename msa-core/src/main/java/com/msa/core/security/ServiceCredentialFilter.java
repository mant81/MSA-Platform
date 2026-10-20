package com.msa.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

public class ServiceCredentialFilter extends OncePerRequestFilter {
  private final ServiceCredentials credentials;

  public ServiceCredentialFilter(ServiceCredentials credentials) {
    this.credentials = credentials;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = request.getServletPath();
    if (path.equals("/actuator/health") || path.startsWith("/actuator/health/")) {
      chain.doFilter(request, response);
      return;
    }
    if (java.util.Collections.list(request.getHeaders("X-Service-Id")).size() != 1
        || java.util.Collections.list(request.getHeaders("X-Service-Token")).size() != 1) {
      reject(response, 401, "SERVICE_AUTH_REQUIRED");
      return;
    }
    String caller = request.getHeader("X-Service-Id");
    if (!credentials.accepts(caller, request.getHeader("X-Service-Token"))) {
      reject(response, 401, "SERVICE_AUTH_REQUIRED");
      return;
    }
    boolean allowed =
        "gateway-service".equals(caller)
            || ("hr-service".equals(credentials.id()) && "member-service".equals(caller))
            || ("trace-service".equals(credentials.id())
                && "POST".equals(request.getMethod())
                && Set.of("/trace-events", "/trace-events/batch").contains(path)
                && Set.of("auth-service", "member-service", "config-service", "hr-service")
                    .contains(caller));
    if (!allowed) {
      reject(response, 403, "SERVICE_ACCESS_DENIED");
      return;
    }
    request.setAttribute("authenticatedServiceId", caller);
    chain.doFilter(request, response);
  }

  private void reject(HttpServletResponse response, int status, String code) throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response.getWriter().write("{\"success\":false,\"code\":\"" + code + "\"}");
  }
}
