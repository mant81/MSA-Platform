package com.msa.member;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.msa.core.security.SecurityFixture;
import com.msa.member.client.TraceClient;
import com.msa.member.client.internal.HrEmployeeFeignClient;
import com.msa.member.service.*;
import com.msa.member.vo.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(
    properties = {"app.trace-service-url=http://127.0.0.1:1", "app.outbox-delay-ms=3600000"})
class ReliableSignupTest {
  static final java.util.concurrent.atomic.AtomicReference<String> ACK_MODE =
      new java.util.concurrent.atomic.AtomicReference<>("unavailable");
  static final com.sun.net.httpserver.HttpServer COLLECTOR;

  static {
    try {
      COLLECTOR =
          com.sun.net.httpserver.HttpServer.create(
              new java.net.InetSocketAddress("127.0.0.1", 0), 0);
      COLLECTOR.createContext(
          "/trace-events/batch",
          exchange -> {
            var json = tools.jackson.databind.json.JsonMapper.builder().build();
            var events = json.readTree(exchange.getRequestBody().readAllBytes());
            var mode = ACK_MODE.get();
            byte[] response;
            int status;
            if (mode.equals("unavailable")) {
              status = 503;
              response = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            } else {
              status = 200;
              var acks = new java.util.ArrayList<com.msa.core.BatchAckVo>();
              for (var event : events) {
                acks.add(
                    new com.msa.core.BatchAckVo(
                        event.path("eventId").asString(),
                        mode.equals("unknown") ? null : "PERSISTED"));
                if (mode.equals("partial")) break;
              }
              response = json.writeValueAsBytes(acks);
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
          });
      COLLECTOR.start();
    } catch (Exception ex) {
      throw new ExceptionInInitializerError(ex);
    }
  }

  static final SecurityFixture SECURITY = new SecurityFixture("member-service");

  @DynamicPropertySource
  static void security(DynamicPropertyRegistry registry) {
    SECURITY.properties().forEach((k, v) -> registry.add(k, () -> v));
    registry.add(
        "app.trace-service-url", () -> "http://127.0.0.1:" + COLLECTOR.getAddress().getPort());
  }

  @AfterAll
  static void cleanup() throws Exception {
    SECURITY.close();
    COLLECTOR.stop(0);
  }

  @Autowired SignupRequestService requests;
  @Autowired TraceClient traces;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean HrDependencyService hr;

  @BeforeEach
  void prepare() {
    ACK_MODE.set("unavailable");
    when(hr.selectEmployee(anyString(), anyString())).thenReturn(Map.of("active", true));
  }

  MemberVo member() {
    var vo = new MemberVo();
    vo.setMemberNo("T-" + UUID.randomUUID().toString().substring(0, 12));
    vo.setMemberName("Fixture");
    vo.setStatus("ACTIVE");
    return vo;
  }

  @Test
  void partialAndUnknownAcknowledgementsNeverDiscardUnapprovedEvents() {
    requests.insert(member(), "partial-ack", "owner", UUID.randomUUID().toString());
    jdbc.update(
        "update trace_outbox set next_attempt_at=? where delivered_at is null",
        java.time.OffsetDateTime.now().minusMinutes(1));
    int before =
        jdbc.queryForObject(
            "select count(*) from trace_outbox where delivered_at is null", Integer.class);
    ACK_MODE.set("unknown");
    assertDoesNotThrow(() -> traces.export());
    assertEquals(
        before,
        jdbc.queryForObject(
            "select count(*) from trace_outbox where delivered_at is null", Integer.class));
    jdbc.update(
        "update trace_outbox set next_attempt_at=? where delivered_at is null",
        java.time.OffsetDateTime.now().minusMinutes(1));
    ACK_MODE.set("partial");
    traces.export();
    assertEquals(
        before - 1,
        jdbc.queryForObject(
            "select count(*) from trace_outbox where delivered_at is null", Integer.class));
    jdbc.update(
        "update trace_outbox set next_attempt_at=? where delivered_at is null",
        java.time.OffsetDateTime.now().minusMinutes(1));
    ACK_MODE.set("all");
    traces.export();
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from trace_outbox where delivered_at is null", Integer.class));
  }

  @Test
  void replayReturnsSameProcessAndSingleBusinessEffect() {
    var member = member();
    String key = UUID.randomUUID().toString();
    var first = requests.insert(member, "trace-a", "owner", key);
    var second = requests.insert(member, "trace-b", "owner", key);
    assertEquals(first.getProcessId(), second.getProcessId());
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from member where member_no=?", Integer.class, member.getMemberNo()));
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from member_process where process_id=?",
            Integer.class,
            first.getProcessId()));
    verify(hr, times(1)).selectEmployee(eq(member.getMemberNo()), anyString());
  }

  @Test
  void payloadConflictRejected() {
    var member = member();
    String key = UUID.randomUUID().toString();
    requests.insert(member, "a", "owner", key);
    member.setMemberName("Different");
    assertEquals(
        409,
        assertThrows(
                ResponseStatusException.class, () -> requests.insert(member, "b", "owner", key))
            .getStatusCode()
            .value());
  }

  @Test
  void concurrentRequestsHaveOneEffect() throws Exception {
    var member = member();
    String key = UUID.randomUUID().toString();
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(6)) {
      var results = new ArrayList<Future<String>>();
      for (int i = 0; i < 6; i++)
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  try {
                    return requests.insert(member, "concurrent", "owner", key).getProcessId();
                  } catch (ResponseStatusException ex) {
                    assertEquals(409, ex.getStatusCode().value());
                    return "RUNNING";
                  }
                }));
      start.countDown();
      for (var result : results) assertNotNull(result.get(10, TimeUnit.SECONDS));
    }
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from member where member_no=?", Integer.class, member.getMemberNo()));
  }

  @Test
  void failedSaveRollsBackStepsProcessAndOutbox() {
    var member = member();
    requests.insert(member, "first", "owner", UUID.randomUUID().toString());
    int processes = jdbc.queryForObject("select count(*) from member_process", Integer.class);
    int outbox = jdbc.queryForObject("select count(*) from trace_outbox", Integer.class);
    assertThrows(
        ResponseStatusException.class,
        () -> requests.insert(member, "duplicate", "owner", UUID.randomUUID().toString()));
    assertEquals(
        processes, jdbc.queryForObject("select count(*) from member_process", Integer.class));
    assertEquals(outbox, jdbc.queryForObject("select count(*) from trace_outbox", Integer.class));
    assertTrue(
        jdbc.queryForObject(
                "select count(*) from signup_request where status='RECOVERY_REQUIRED'",
                Integer.class)
            > 0);
  }

  @Test
  void collectorFailureDoesNotLoseCommittedOutbox() {
    var member = member();
    requests.insert(member, "outbox-test", "owner", UUID.randomUUID().toString());
    int before =
        jdbc.queryForObject(
            "select count(*) from trace_outbox where delivered_at is null", Integer.class);
    traces.export();
    assertEquals(
        before,
        jdbc.queryForObject(
            "select count(*) from trace_outbox where delivered_at is null", Integer.class));
    assertTrue(
        jdbc.queryForObject("select count(*) from trace_outbox where attempts>0", Integer.class)
            > 0);
  }

  @Test
  void orphanClaimBecomesRecoveryRequiredWithoutAutomaticSideEffects() {
    String key = UUID.randomUUID().toString().replace("-", "");
    jdbc.update(
        "insert into signup_request(request_key,fingerprint,status,created_at,updated_at) values"
            + " (?,?,'RUNNING',?,?)",
        key,
        key,
        java.time.OffsetDateTime.now().minusMinutes(10),
        java.time.OffsetDateTime.now().minusMinutes(10));
    requests.recover();
    assertEquals(
        "RECOVERY_REQUIRED",
        jdbc.queryForObject(
            "select status from signup_request where request_key=?", String.class, key));
  }

  @Test
  void circuitOpensWithoutCallingFailedDependencyAgain() throws Exception {
    HrEmployeeFeignClient client = mock(HrEmployeeFeignClient.class);
    when(client.selectByEmployeeNo(anyString(), anyString()))
        .thenThrow(new IllegalStateException("fixture"));
    var dependency = new HrDependencyService(client);
    for (int i = 0; i < 5; i++)
      assertThrows(RuntimeException.class, () -> dependency.selectEmployee("x", "trace"));
    assertEquals("OPEN", dependency.selectState());
    assertThrows(
        io.github.resilience4j.circuitbreaker.CallNotPermittedException.class,
        () -> dependency.selectEmployee("x", "trace"));
    verify(client, times(5)).selectByEmployeeNo(anyString(), anyString());
    doReturn(Map.of("active", true)).when(client).selectByEmployeeNo(anyString(), anyString());
    Thread.sleep(5100);
    assertEquals(true, dependency.selectEmployee("x", "trace").get("active"));
    assertEquals("CLOSED", dependency.selectState());
  }
}
