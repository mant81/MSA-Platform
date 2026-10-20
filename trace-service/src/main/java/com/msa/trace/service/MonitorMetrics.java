package com.msa.trace.service;

import io.micrometer.core.instrument.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MonitorMetrics {
  public MonitorMetrics(JdbcTemplate jdbc, MeterRegistry meters) {
    Gauge.builder(
            "msa.alert.pending",
            jdbc,
            j ->
                j.queryForObject(
                    "select count(*) from alert_outbox where delivered_at is null", Long.class))
        .register(meters);
    Gauge.builder(
            "msa.incident.open",
            jdbc,
            j ->
                j.queryForObject(
                    "select count(*) from operational_incident where state='OPEN'", Long.class))
        .register(meters);
  }
}
