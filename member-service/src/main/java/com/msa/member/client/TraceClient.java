package com.msa.member.client;

import com.msa.core.TraceEventVo;
import com.msa.core.security.ServiceCredentials;
import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class TraceClient {
  private static final Logger log = LoggerFactory.getLogger(TraceClient.class);
  private final RestClient restClient;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  private final tools.jackson.databind.json.JsonMapper json =
      tools.jackson.databind.json.JsonMapper.builder().build();

  public void insertDurable(TraceEventVo event) {
    if (!org.springframework.transaction.support.TransactionSynchronizationManager
        .isActualTransactionActive())
      throw new IllegalStateException("Business trace requires a transaction");
    if (event.getEventId() == null) event.setEventId(java.util.UUID.randomUUID().toString());
    var now = java.time.OffsetDateTime.now();
    jdbc.update(
        "insert into trace_outbox(event_id,payload,created_at,next_attempt_at) values (?,?,?,?)",
        event.getEventId(),
        json.writeValueAsString(event),
        now,
        now);
  }

  @org.springframework.scheduling.annotation.Scheduled(
      fixedDelayString = "${app.outbox-delay-ms:1000}")
  public void export() {
    // At-least-once across workers/restarts; receiver acknowledges duplicate event IDs.
    var rows =
        jdbc.queryForList(
            "select event_id,payload,attempts from trace_outbox where delivered_at is null and"
                + " next_attempt_at <= ? order by created_at limit 100",
            java.time.OffsetDateTime.now());
    if (rows.isEmpty()) return;
    java.util.Set<String> accepted = new java.util.HashSet<>();
    try {
      var events =
          rows.stream()
              .map(
                  row ->
                      json.readValue(
                          row.getOrDefault("PAYLOAD", row.get("payload")).toString(),
                          TraceEventVo.class))
              .toList();
      var acknowledgements =
          restClient
              .post()
              .uri("/trace-events/batch")
              .body(events)
              .retrieve()
              .body(com.msa.core.BatchAckVo[].class);
      if (acknowledgements != null)
        for (var ack : acknowledgements)
          if (ack != null && ("PERSISTED".equals(ack.status()) || "DUPLICATE".equals(ack.status())))
            accepted.add(ack.eventId());
    } catch (RestClientException ex) {
      /* No acknowledgement: retain the entire durable batch. */
    }
    for (var row : rows) {
      String id = row.getOrDefault("EVENT_ID", row.get("event_id")).toString();
      if (accepted.contains(id))
        jdbc.update(
            "update trace_outbox set delivered_at=? where event_id=?",
            java.time.OffsetDateTime.now(),
            id);
      else {
        int attempts = ((Number) row.getOrDefault("ATTEMPTS", row.get("attempts"))).intValue();
        jdbc.update(
            "update trace_outbox set attempts=attempts+1,next_attempt_at=? where event_id=?",
            java.time.OffsetDateTime.now().plusSeconds(Math.min(60, 1L << Math.min(attempts, 6))),
            id);
      }
    }
  }

  public TraceClient(
      @Value("${app.trace-service-url:http://localhost:8090}") String traceServiceUrl,
      ServiceCredentials credentials,
      org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.jdbc = jdbc;
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build());
    factory.setReadTimeout(Duration.ofMillis(500));
    this.restClient =
        RestClient.builder()
            .baseUrl(traceServiceUrl)
            .requestFactory(factory)
            .defaultHeaders(headers -> credentials.apply(headers, "trace-service"))
            .build();
  }

  public void insert(TraceEventVo vo) {
    try {
      restClient.post().uri("/trace-events").body(vo).retrieve().toBodilessEntity();
    } catch (RestClientException ex) {
      log.warn("Member Trace delivery failed; eventType={}", vo.getEventType());
    }
  }
}
