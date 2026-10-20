package com.msa.trace.service;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Expected targets come from trusted operator configuration; API callers cannot supply probe URLs.
 */
@Service
public class ServiceMonitor {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofMillis(500))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();
  private final JsonMapper json = JsonMapper.builder().build();
  private final AtomicReference<Instant> lastPoll = new AtomicReference<>();
  private final int staleSeconds;

  public ServiceMonitor(
      JdbcTemplate jdbc,
      org.springframework.transaction.PlatformTransactionManager manager,
      @Value("${app.monitor.targets:}") String targets,
      @Value("${app.monitor.stale-seconds:30}") int staleSeconds) {
    this.jdbc = jdbc;
    this.tx = new TransactionTemplate(manager);
    this.staleSeconds = staleSeconds;
    // Explicit instance=service@http://host:port list; no credentials/paths/query or redirect.
    for (String target : targets.split(",")) {
      if (target.isBlank()) continue;
      String[] parts = target.trim().split("=", 2);
      String[] endpoint = parts[1].split("@", 2);
      URI uri = URI.create(endpoint[1]);
      if (!parts[0].matches("[A-Za-z0-9._-]{1,100}")
          || !endpoint[0].matches("[A-Za-z0-9._-]{1,100}")
          || !Set.of("http", "https").contains(uri.getScheme())
          || uri.getHost() == null
          || uri.getUserInfo() != null
          || uri.getRawQuery() != null
          || uri.getRawFragment() != null
          || !Set.of("", "/").contains(uri.getPath()))
        throw new IllegalArgumentException("Invalid monitor target");
      int updated =
          jdbc.update(
              "update service_instance set service_name=?,base_url=? where instance_id=?",
              endpoint[0],
              uri.toString(),
              parts[0]);
      if (updated == 0)
        jdbc.update(
            "insert into service_instance(instance_id,service_name,base_url) values (?,?,?)",
            parts[0],
            endpoint[0],
            uri.toString());
    }
  }

  @Scheduled(fixedDelayString = "${app.monitor.interval-ms:5000}")
  public void poll() {
    for (var row :
        jdbc.queryForList("select instance_id,base_url from service_instance where enabled=true")) {
      String id = value(row, "instance_id");
      String base = value(row, "base_url");
      String outcome = "DOWN", reason = "PROBE_UNREACHABLE";
      try {
        var response =
            client.send(
                HttpRequest.newBuilder(
                        URI.create(base.replaceAll("/$", "") + "/actuator/health/readiness"))
                    .timeout(Duration.ofMillis(1000))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200
            && "UP".equals(json.readTree(response.body()).path("status").asString())) {
          outcome = "UP";
          reason = "READINESS_UP";
        } else {
          outcome = "DOWN";
          reason = "READINESS_NOT_READY";
        }
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return;
      } catch (Exception ex) {
        /* bounded safe reason, never persist response bodies */
      }
      record(id, outcome, reason);
    }
    lastPoll.set(Instant.now());
  }

  private String value(Map<String, Object> row, String key) {
    return row.getOrDefault(key.toUpperCase(Locale.ROOT), row.get(key)).toString();
  }

  public void record(String id, String outcome, String reason) {
    tx.executeWithoutResult(
        status -> {
          var row =
              jdbc.queryForMap("select * from service_instance where instance_id=? for update", id);
          int failures = "UP".equals(outcome) ? 0 : Integer.parseInt(value(row, "failures")) + 1;
          int successes = "UP".equals(outcome) ? Integer.parseInt(value(row, "successes")) + 1 : 0;
          String previous = value(row, "state");
          String state =
              "UP".equals(outcome)
                  ? (successes >= 2 ? "UP" : "DEGRADED")
                  : (failures >= 3 ? outcome : "DEGRADED");
          // first failure is degraded; three consecutive failures confirm down, recovery needs two
          // successes.
          jdbc.update(
              "update service_instance set state=?,failures=?,successes=?,last_checked=?,reason=?"
                  + " where instance_id=?",
              state,
              failures,
              successes,
              OffsetDateTime.now(),
              reason,
              id);
          if (!state.equals(previous)) {
            jdbc.update(
                "insert into"
                    + " service_state_history(instance_id,previous_state,state,reason,observed_at)"
                    + " values (?,?,?,?,?)",
                id,
                previous,
                state,
                reason,
                OffsetDateTime.now());
          }
          Object maintenance = row.getOrDefault("MAINTENANCE_UNTIL", row.get("maintenance_until"));
          boolean muted =
              maintenance instanceof OffsetDateTime t && t.isAfter(OffsetDateTime.now());
          if ("DOWN".equals(state)
              && !muted
              && jdbc.queryForObject(
                      "select count(*) from operational_incident where instance_id=? and"
                          + " state='OPEN'",
                      Integer.class,
                      id)
                  == 0) {
            String incident = UUID.randomUUID().toString();
            jdbc.update(
                "insert into operational_incident(incident_id,instance_id,opened_at,state) values"
                    + " (?,?,?,'OPEN')",
                incident,
                id,
                OffsetDateTime.now());
            notification(incident, id, "OPENED");
          }
          if ("UP".equals(state)) {
            for (var active :
                jdbc.queryForList(
                    "select incident_id from operational_incident where instance_id=? and"
                        + " state='OPEN'",
                    id)) notification(value(active, "incident_id"), id, "RECOVERED");
            jdbc.update(
                "update operational_incident set state='RECOVERED',recovered_at=? where"
                    + " instance_id=? and state='OPEN'",
                OffsetDateTime.now(),
                id);
          }
        });
  }

  private void notification(String incident, String instance, String type) {
    jdbc.update(
        "insert into"
            + " alert_outbox(event_id,incident_id,event_type,instance_id,created_at,next_attempt_at)"
            + " values (?,?,?,?,?,?)",
        UUID.randomUUID().toString(),
        incident,
        type,
        instance,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  public int maintenance(String id, boolean enabled, String actor) {
    return tx.execute(
        status -> {
          int count =
              jdbc.update(
                  "update service_instance set maintenance_until=? where instance_id=?",
                  enabled ? OffsetDateTime.now().plusHours(1) : null,
                  id);
          if (count > 0)
            jdbc.update(
                "insert into operational_audit(event_id,actor,action,target_id,created_at) values"
                    + " (?,?,'MAINTENANCE_CHANGED',?,?)",
                UUID.randomUUID().toString(),
                actor,
                id,
                OffsetDateTime.now());
          return count;
        });
  }

  public Map<String, Object> selectStatus() {
    var now = Instant.now();
    var instances = new ArrayList<Map<String, Object>>();
    var services = new TreeMap<String, List<String>>();
    for (var row :
        jdbc.queryForList(
            "select"
                + " instance_id,service_name,state,failures,successes,last_checked,reason,policy_version,maintenance_until"
                + " from service_instance where enabled=true order by service_name,instance_id")) {
      Map<String, Object> item = new LinkedHashMap<>();
      row.forEach((k, v) -> item.put(k.toLowerCase(Locale.ROOT), v));
      Object checked = item.get("last_checked");
      Instant time =
          checked instanceof OffsetDateTime t
              ? t.toInstant()
              : checked instanceof java.sql.Timestamp t ? t.toInstant() : null;
      if (time == null || Duration.between(time, now).getSeconds() > staleSeconds) {
        item.put("state", "UNKNOWN");
        item.put("reason", "OBSERVATION_STALE");
      }
      instances.add(item);
      services
          .computeIfAbsent(item.get("service_name").toString(), k -> new ArrayList<>())
          .add(item.get("state").toString());
    }
    Map<String, String> aggregated = new TreeMap<>();
    services.forEach(
        (name, states) ->
            aggregated.put(
                name,
                states.contains("UNKNOWN")
                    ? "UNKNOWN"
                    : states.stream().allMatch("UP"::equals)
                        ? "UP"
                        : states.stream().allMatch("DOWN"::equals) ? "DOWN" : "DEGRADED"));
    return Map.of(
        "observedAt",
        now.toString(),
        "policyVersion",
        "1",
        "instances",
        instances,
        "services",
        aggregated,
        "collectorState",
        lastPoll.get() == null || Duration.between(lastPoll.get(), now).getSeconds() > staleSeconds
            ? "UNKNOWN"
            : "UP");
  }

  public List<Map<String, Object>> selectIncidents() {
    return jdbc.queryForList(
        "select * from operational_incident order by opened_at desc limit 100");
  }

  public List<Map<String, Object>> selectHistory() {
    return jdbc.queryForList("select * from service_state_history order by id desc limit 200");
  }

  public int acknowledge(String id, String actor) {
    return tx.execute(
        status -> {
          int count =
              jdbc.update(
                  "update operational_incident set acknowledged_at=?,acknowledged_by=? where"
                      + " incident_id=? and state='OPEN' and acknowledged_at is null",
                  OffsetDateTime.now(),
                  actor,
                  id);
          if (count > 0)
            jdbc.update(
                "insert into operational_audit(event_id,actor,action,target_id,created_at) values"
                    + " (?,?,'INCIDENT_ACK',?,?)",
                UUID.randomUUID().toString(),
                actor,
                id,
                OffsetDateTime.now());
          return count;
        });
  }
}
