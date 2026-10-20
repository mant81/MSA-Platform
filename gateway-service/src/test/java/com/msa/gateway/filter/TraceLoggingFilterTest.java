package com.msa.gateway.filter;

import static org.junit.jupiter.api.Assertions.*;

import com.msa.core.security.ServiceCredentials;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

class TraceLoggingFilterTest {
  private HttpServer server;
  private TraceLoggingFilter filter;
  private final List<Map<?, ?>> events = new CopyOnWriteArrayList<>();
  private CountDownLatch received;
  private final CountDownLatch release = new CountDownLatch(1);
  private volatile boolean slow;

  @BeforeEach
  void startCollector() throws Exception {
    received = new CountDownLatch(2);
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/trace-events",
        exchange -> {
          events.add(
              JsonMapper.builder()
                  .build()
                  .readValue(exchange.getRequestBody().readAllBytes(), Map.class));
          received.countDown();
          if (slow) {
            try {
              release.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
              Thread.currentThread().interrupt();
            }
          }
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    server.start();
    filter =
        new TraceLoggingFilter(
            "http://127.0.0.1:" + server.getAddress().getPort(),
            new ServiceCredentials(
                "gateway-service", Map.of("trace-service", "g".repeat(64)), Map.of()));
  }

  @AfterEach
  void stop() {
    release.countDown();
    filter.stop();
    server.stop(0);
  }

  private MockServerWebExchange exchange() {
    return MockServerWebExchange.from(
        MockServerHttpRequest.get("/configs").header("X-Trace-Id", "trace-regression"));
  }

  @Test
  void recordsSuccessfulRequestWithTheSameTraceId() throws Exception {
    var exchange = exchange();
    filter
        .filter(
            exchange,
            next -> {
              assertEquals(
                  "trace-regression", next.getRequest().getHeaders().getFirst("X-Trace-Id"));
              next.getResponse().setStatusCode(HttpStatus.OK);
              return Mono.empty();
            })
        .block(Duration.ofSeconds(2));
    assertTrue(received.await(3, TimeUnit.SECONDS));
    assertEquals("RUNNING", events.get(0).get("status"));
    assertEquals("SUCCESS", events.get(1).get("status"));
    assertEquals("trace-regression", events.get(1).get("legacyTraceId"));
    assertTrue(events.get(1).get("traceId").toString().matches("[0-9a-f]{32}"));
    assertTrue(events.get(1).get("spanId").toString().matches("[0-9a-f]{16}"));
  }

  @ParameterizedTest
  @ValueSource(ints = {401, 422, 503})
  void recordsHttpErrorsAsFailed(int status) throws Exception {
    filter
        .filter(
            exchange(),
            next -> {
              next.getResponse().setStatusCode(HttpStatusCode.valueOf(status));
              return Mono.empty();
            })
        .block(Duration.ofSeconds(2));
    assertTrue(received.await(3, TimeUnit.SECONDS));
    assertEquals("FAILED", events.get(1).get("status"));
    assertEquals("HTTP_" + status, events.get(1).get("errorCode"));
  }

  @Test
  void preservesBusinessResponseWhenCollectorIsSlow() {
    slow = true;
    var exchange = exchange();
    filter
        .filter(
            exchange,
            next -> {
              next.getResponse().setStatusCode(HttpStatus.OK);
              return Mono.empty();
            })
        .block(Duration.ofMillis(250));
    assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());
  }

  @Test
  void doesNotSwallowRoutingExceptions() throws Exception {
    assertThrows(
        IllegalStateException.class,
        () ->
            filter
                .filter(exchange(), next -> Mono.error(new IllegalStateException("route failed")))
                .block(Duration.ofSeconds(2)));
    assertTrue(received.await(3, TimeUnit.SECONDS));
    assertEquals("FAILED", events.get(1).get("status"));
    assertEquals("GATEWAY_ERROR", events.get(1).get("errorCode"));
  }
}
