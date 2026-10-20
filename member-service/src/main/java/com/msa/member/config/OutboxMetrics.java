package com.msa.member.config;

import io.micrometer.core.instrument.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
public class OutboxMetrics {
  public OutboxMetrics(JdbcTemplate jdbc, MeterRegistry meters) {
    Gauge.builder(
            "msa.outbox.pending",
            jdbc,
            j ->
                j.queryForObject(
                    "select count(*) from trace_outbox where delivered_at is null", Long.class))
        .register(meters);
    Gauge.builder(
            "msa.outbox.retrying",
            jdbc,
            j ->
                j.queryForObject(
                    "select count(*) from trace_outbox where delivered_at is null and attempts>0",
                    Long.class))
        .register(meters);
  }
}
