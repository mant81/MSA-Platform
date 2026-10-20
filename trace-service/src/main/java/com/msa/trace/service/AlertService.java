package com.msa.trace.service;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AlertService {
  private final JdbcTemplate jdbc;
  private final URI target;
  private final String token;
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofMillis(500))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();
  private final JsonMapper json = JsonMapper.builder().build();

  public AlertService(
      JdbcTemplate jdbc,
      @Value("${app.alert.webhook-url:}") String url,
      @Value("${app.alert.token:}") String token,
      @Value("${app.alert.allow-loopback-http:false}") boolean local) {
    this.jdbc = jdbc;
    this.token = token;
    this.target = url.isBlank() ? null : URI.create(url);
    if (target != null
        && (target.getHost() == null
            || target.getUserInfo() != null
            || target.getRawFragment() != null
            || !("https".equals(target.getScheme())
                || (local
                    && "http".equals(target.getScheme())
                    && Set.of("127.0.0.1", "localhost", "[::1]").contains(target.getHost())))))
      throw new IllegalArgumentException("Webhook requires HTTPS");
  }

  @Scheduled(fixedDelayString = "${app.alert.interval-ms:5000}")
  public void send() {
    if (target == null) return;
    for (var row :
        jdbc.queryForList(
            "select * from alert_outbox where delivered_at is null and next_attempt_at<=? order by"
                + " created_at limit 50",
            OffsetDateTime.now())) {
      Map<String, Object> message = new LinkedHashMap<>();
      row.forEach((k, v) -> message.put(k.toLowerCase(Locale.ROOT), v));
      String id = message.get("event_id").toString();
      try {
        var builder =
            HttpRequest.newBuilder(target)
                .timeout(Duration.ofSeconds(2))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", id);
        if (!token.isBlank()) builder.header("Authorization", "Bearer " + token);
        var response =
            client.send(
                builder
                    .POST(
                        HttpRequest.BodyPublishers.ofString(
                            json.writeValueAsString(
                                Map.of(
                                    "eventId",
                                    id,
                                    "incidentId",
                                    message.get("incident_id"),
                                    "instanceId",
                                    message.get("instance_id"),
                                    "eventType",
                                    message.get("event_type"),
                                    "occurredAt",
                                    message.get("created_at").toString(),
                                    "runbook",
                                    "docs/operations/RUNBOOK.md"))))
                    .build(),
                HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
          throw new IllegalStateException("Webhook not acknowledged");
        jdbc.update(
            "update alert_outbox set delivered_at=? where event_id=?", OffsetDateTime.now(), id);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return;
      } catch (Exception ex) {
        int attempts = ((Number) message.get("attempts")).intValue();
        jdbc.update(
            "update alert_outbox set attempts=attempts+1,next_attempt_at=? where event_id=?",
            OffsetDateTime.now().plusSeconds(Math.min(300, 1L << Math.min(attempts, 8))),
            id);
      }
    }
  }

  public Map<String, Object> selectDelivery() {
    return Map.of(
        "configured",
        target != null,
        "pending",
        jdbc.queryForObject(
            "select count(*) from alert_outbox where delivered_at is null", Long.class),
        "deliveryGuarantee",
        "AT_LEAST_ONCE");
  }
}
