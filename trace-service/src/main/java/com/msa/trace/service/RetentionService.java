package com.msa.trace.service;

import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RetentionService {
  private final JdbcTemplate jdbc;
  private final boolean enabled;
  private final int days;

  public RetentionService(
      JdbcTemplate jdbc,
      @Value("${app.retention.enabled:false}") boolean enabled,
      @Value("${app.retention.trace-days:30}") int days) {
    if (days < 1) throw new IllegalArgumentException("Retention must be positive");
    this.jdbc = jdbc;
    this.enabled = enabled;
    this.days = days;
  }

  @Scheduled(fixedDelayString = "${app.retention.interval-ms:3600000}")
  public void cleanup() {
    if (enabled) cleanupBefore(OffsetDateTime.now().minusDays(days));
  }

  public int cleanupBefore(OffsetDateTime threshold) {
    // One bounded batch per run; pending notifications/incidents are never deleted.
    return jdbc.update(
        "delete from trace_event where id in (select id from trace_event where created_at < ? order"
            + " by id limit 500)",
        threshold);
  }
}
