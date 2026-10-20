package com.msa.core.security;

import com.msa.core.TraceContext;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

public class TraceContextFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    TraceContext context =
        TraceContext.receive(request.getHeader("traceparent"), request.getHeader("X-Trace-Id"));
    request.setAttribute("traceContext", context);
    request.setAttribute("traceId", context.correlationId());
    response.setHeader("traceparent", context.header());
    response.setHeader("X-Trace-Id", context.correlationId());
    chain.doFilter(request, response);
  }
}
