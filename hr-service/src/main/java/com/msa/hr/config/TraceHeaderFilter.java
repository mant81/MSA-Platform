package com.msa.hr.config;

import com.msa.core.*;
import com.msa.core.security.ServiceCredentials;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TraceHeaderFilter extends OncePerRequestFilter {
  private final ThreadPoolExecutor sender;
  private final RestClient collector;
  private final AtomicLong dropped = new AtomicLong();

  public TraceHeaderFilter(
      @Value("${app.trace-service-url:http://localhost:8090}") String url,
      ServiceCredentials credentials,
      io.micrometer.core.instrument.MeterRegistry meters) {
    var factory =
        new JdkClientHttpRequestFactory(
            java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build());
    factory.setReadTimeout(Duration.ofMillis(500));
    collector =
        RestClient.builder()
            .baseUrl(url)
            .requestFactory(factory)
            .defaultHeaders(h -> credentials.apply(h, "trace-service"))
            .build();
    sender =
        new ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(256),
            r -> {
              var t = new Thread(r, "hr-trace-exporter");
              t.setDaemon(true);
              return t;
            },
            (r, p) -> dropped.incrementAndGet());
    io.micrometer.core.instrument.Gauge.builder(
            "msa.trace.dropped", dropped, AtomicLong::doubleValue)
        .register(meters);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getRequestURI().startsWith("/actuator/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long start = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      if (request.getAttribute("traceContext") instanceof TraceContext context) {
        var event = new TraceEventVo();
        context.apply(event);
        event.setServiceName("hr-service");
        event.setEventType(response.getStatus() >= 400 ? "REQUEST_FAILED" : "REQUEST_COMPLETED");
        event.setStatus(response.getStatus() >= 400 ? "FAILED" : "SUCCESS");
        event.setHttpMethod(request.getMethod());
        event.setPath("/hr/employees/{employeeNo}");
        event.setCreatedAt(OffsetDateTime.now());
        event.setElapsedMs((System.nanoTime() - start) / 1000000);
        String process = request.getHeader("X-Process-Id");
        if ("member-service".equals(request.getAttribute("authenticatedServiceId"))
            && process != null
            && process.matches("[A-Za-z0-9._:-]{1,100}")) event.setProcessId(process);
        sender.execute(
            () -> {
              try {
                collector.post().uri("/trace-events").body(event).retrieve().toBodilessEntity();
              } catch (Exception error) {
                dropped.incrementAndGet();
              }
            });
      }
    }
  }

  @jakarta.annotation.PreDestroy
  public void stop() {
    sender.shutdownNow();
  }
}
