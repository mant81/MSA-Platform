package com.msa.trace;

import static org.junit.jupiter.api.Assertions.*;

import com.msa.core.*;
import com.msa.core.security.SecurityFixture;
import com.msa.trace.service.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {"app.monitor.targets=", "app.monitor.interval-ms=3600000"})
class OperationsIntegrationTest {
  static final java.util.concurrent.atomic.AtomicInteger WEBHOOK_STATUS =
      new java.util.concurrent.atomic.AtomicInteger(204);
  static final java.util.concurrent.atomic.AtomicInteger RECEIVED =
      new java.util.concurrent.atomic.AtomicInteger();
  static final com.sun.net.httpserver.HttpServer WEBHOOK;

  static {
    try {
      WEBHOOK =
          com.sun.net.httpserver.HttpServer.create(
              new java.net.InetSocketAddress("127.0.0.1", 0), 0);
      WEBHOOK.createContext(
          "/alert",
          exchange -> {
            exchange.getRequestBody().readAllBytes();
            RECEIVED.incrementAndGet();
            exchange.sendResponseHeaders(WEBHOOK_STATUS.get(), -1);
            exchange.close();
          });
      WEBHOOK.start();
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @Autowired AlertService alerts;
  @Autowired RetentionService retention;
  static final SecurityFixture SECURITY = new SecurityFixture("trace-service");

  @DynamicPropertySource
  static void security(DynamicPropertyRegistry r) {
    SECURITY.properties().forEach((k, v) -> r.add(k, () -> v));
    r.add(
        "app.alert.webhook-url",
        () -> "http://127.0.0.1:" + WEBHOOK.getAddress().getPort() + "/alert");
    r.add("app.alert.allow-loopback-http", () -> "true");
    r.add("app.alert.interval-ms", () -> "3600000");
  }

  @AfterAll
  static void cleanup() throws Exception {
    SECURITY.close();
    WEBHOOK.stop(0);
  }

  @Autowired TraceService traces;
  @Autowired OperationsService operations;
  @Autowired ServiceMonitor monitor;
  @Autowired JdbcTemplate jdbc;

  TraceEventVo event(String process, String type, String status, long version) {
    var e = new TraceEventVo();
    e.setTraceId(TraceContext.id());
    e.setEventId(UUID.randomUUID().toString());
    e.setProcessId(process);
    e.setSourceService("member-service");
    e.setEventType(type);
    e.setStatus(status);
    e.setAggregateVersion(version);
    e.setCreatedAt(OffsetDateTime.now());
    return e;
  }

  @Test
  void legacyCorrelationSupportsCombinedProcessAndTraceLookup() {
    String process = UUID.randomUUID().toString();
    var event = event(process, "REQUEST_COMPLETED", "SUCCESS", 10);
    event.setLegacyTraceId("legacy-combined-id");
    traces.insert(event);
    assertEquals(1, traces.selectByProcessIdAndTraceId(process, "legacy-combined-id").size());
    assertEquals(1, traces.selectByProcessIdAndTraceId(process, event.getTraceId()).size());
  }

  @Test
  void replayAndReverseDeliveryDoNotRegressTerminalProjection() {
    String id = UUID.randomUUID().toString();
    var done = event(id, "REQUEST_COMPLETED", "SUCCESS", 10);
    assertEquals(1, traces.insert(done));
    assertEquals(0, traces.insert(done));
    traces.insert(event(id, "REQUEST_STARTED", "RUNNING", 1));
    assertEquals(
        "SUCCEEDED",
        jdbc.queryForObject(
            "select status from flow_projection where process_id=?", String.class, id));
    assertEquals(
        2,
        jdbc.queryForObject(
            "select count(*) from trace_event where process_id=?", Integer.class, id));
    assertEquals(
        10,
        jdbc.queryForObject(
            "select aggregate_version from flow_projection where process_id=?", Integer.class, id));
  }

  @Test
  void forgedPublisherCannotProjectMemberWorkflow() {
    String id = UUID.randomUUID().toString();
    var e = event(id, "REQUEST_COMPLETED", "SUCCESS", 100);
    e.setSourceService("config-service");
    traces.insert(e);
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from flow_projection where process_id=?", Integer.class, id));
  }

  @Test
  void cursorIsBoundedAndErrorTextIsSanitized() {
    var e = event(UUID.randomUUID().toString(), "REQUEST_FAILED", "FAILED", 10);
    e.setErrorCode("DATABASE_FAILURE");
    e.setErrorMessage("secret-password-fixture");
    traces.insert(e);
    var page = operations.selectFlow(0, 1, null, null, null);
    assertTrue(((List<?>) page.get("events")).size() <= 1);
    assertTrue(page.containsKey("nextCursor"));
    assertEquals("UNVERIFIED", page.get("completeness"));
    assertFalse(
        traces
            .selectByProcessId(e.getProcessId())
            .getFirst()
            .getErrorMessage()
            .contains("secret-password"));
    assertTrue(operations.selectErrors(OffsetDateTime.now().minusHours(1)).size() > 0);
  }

  @Test
  void malformedW3cContextGetsFreshIdsAndValidParentIsPreserved() {
    var fresh =
        TraceContext.receive("00-" + "0".repeat(32) + "-" + "0".repeat(16) + "-01", "legacy");
    assertNotEquals("0".repeat(32), fresh.traceId());
    assertNull(fresh.parentSpanId());
    var parent = TraceContext.receive(null, null);
    var child = TraceContext.receive(parent.header(), null);
    assertEquals(parent.traceId(), child.traceId());
    assertEquals(parent.spanId(), child.parentSpanId());
    assertNotEquals(parent.spanId(), child.spanId());
  }

  @Test
  void actualWebhookRetryAndRecoveryDelivery() {
    String id = instance();
    for (int i = 0; i < 3; i++) monitor.record(id, "DOWN", "PROBE_UNREACHABLE");
    WEBHOOK_STATUS.set(503);
    alerts.send();
    assertTrue(
        jdbc.queryForObject(
                "select count(*) from alert_outbox where instance_id=? and attempts>0 and"
                    + " delivered_at is null",
                Integer.class,
                id)
            > 0);
    WEBHOOK_STATUS.set(204);
    jdbc.update(
        "update alert_outbox set next_attempt_at=? where instance_id=?",
        OffsetDateTime.now().minusMinutes(1),
        id);
    alerts.send();
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from alert_outbox where instance_id=? and delivered_at is not null",
            Integer.class,
            id));
    monitor.record(id, "UP", "READINESS_UP");
    monitor.record(id, "UP", "READINESS_UP");
    alerts.send();
    assertEquals(
        2,
        jdbc.queryForObject(
            "select count(*) from alert_outbox where instance_id=? and delivered_at is not null",
            Integer.class,
            id));
    assertTrue(RECEIVED.get() >= 3);
  }

  @Test
  void maintenanceSuppressesNewIncidentButPreservesState() {
    String id = instance();
    monitor.maintenance(id, true, "operator");
    for (int i = 0; i < 4; i++) monitor.record(id, "DOWN", "PROBE_UNREACHABLE");
    assertEquals(
        "DOWN",
        jdbc.queryForObject(
            "select state from service_instance where instance_id=?", String.class, id));
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from operational_incident where instance_id=?", Integer.class, id));
    monitor.maintenance(id, false, "operator");
    monitor.record(id, "DOWN", "PROBE_UNREACHABLE");
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from operational_incident where instance_id=?", Integer.class, id));
  }

  @Test
  void retentionDeletesExpiredTraceOnly() {
    var old = event(UUID.randomUUID().toString(), "REQUEST_COMPLETED", "SUCCESS", 10);
    old.setCreatedAt(OffsetDateTime.now().minusDays(100));
    traces.insert(old);
    var fresh = event(UUID.randomUUID().toString(), "REQUEST_COMPLETED", "SUCCESS", 10);
    traces.insert(fresh);
    assertTrue(retention.cleanupBefore(OffsetDateTime.now().minusDays(30)) > 0);
    assertTrue(traces.selectByProcessId(old.getProcessId()).isEmpty());
    assertEquals(1, traces.selectByProcessId(fresh.getProcessId()).size());
  }

  String instance() {
    String id = "i-" + UUID.randomUUID().toString().substring(0, 8);
    jdbc.update(
        "insert into service_instance(instance_id,service_name,base_url) values"
            + " (?,'test-service','http://127.0.0.1:1')",
        id);
    return id;
  }

  @Test
  void failureRecoveryIncidentAckAndStalenessAreDistinct() {
    String id = instance();
    monitor.record(id, "DOWN", "PROBE_UNREACHABLE");
    assertEquals(
        "DEGRADED",
        jdbc.queryForObject(
            "select state from service_instance where instance_id=?", String.class, id));
    monitor.record(id, "DOWN", "PROBE_UNREACHABLE");
    monitor.record(id, "DOWN", "PROBE_UNREACHABLE");
    monitor.record(id, "DOWN", "PROBE_UNREACHABLE");
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from operational_incident where instance_id=? and state='OPEN'",
            Integer.class,
            id));
    String incident =
        jdbc.queryForObject(
            "select incident_id from operational_incident where instance_id=?", String.class, id);
    assertEquals(1, monitor.acknowledge(incident, "operator"));
    assertEquals(0, monitor.acknowledge(incident, "operator"));
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from operational_audit where target_id=?", Integer.class, incident));
    monitor.record(id, "UP", "READINESS_UP");
    monitor.record(id, "UP", "READINESS_UP");
    assertEquals(
        "RECOVERED",
        jdbc.queryForObject(
            "select state from operational_incident where incident_id=?", String.class, incident));
    jdbc.update(
        "update service_instance set last_checked=? where instance_id=?",
        OffsetDateTime.now().minusMinutes(10),
        id);
    String status = monitor.selectStatus().toString();
    assertTrue(status.contains("UNKNOWN"));
    assertTrue(status.contains("OBSERVATION_STALE"));
  }
}
