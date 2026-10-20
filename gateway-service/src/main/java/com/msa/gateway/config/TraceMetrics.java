package com.msa.gateway.config;

import com.msa.gateway.filter.TraceLoggingFilter;
import io.micrometer.core.instrument.*;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TraceMetrics {
  public TraceMetrics(TraceLoggingFilter filter, MeterRegistry meters) {
    Gauge.builder("msa.trace.dropped", filter, TraceLoggingFilter::selectDroppedEvents)
        .register(meters);
    Gauge.builder("msa.trace.failed", filter, TraceLoggingFilter::selectFailedEvents)
        .register(meters);
  }
}
