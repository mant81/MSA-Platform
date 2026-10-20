package com.msa.core;

import java.util.UUID;
import java.util.regex.Pattern;

/** Immutable W3C version 00 context. Never kept in a global or thread-local variable. */
public record TraceContext(
    String traceId, String spanId, String parentSpanId, String legacyTraceId, String flags) {
  private static final Pattern PARENT = Pattern.compile("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}");

  public TraceContext(String traceId, String spanId, String parentSpanId, String legacyTraceId) {
    this(traceId, spanId, parentSpanId, legacyTraceId, "01");
  }

  public static String id() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  public static TraceContext receive(String parent, String legacy) {
    String trace = id(), upstream = null, flags = "01";
    if (parent != null && PARENT.matcher(parent).matches()) {
      String[] parts = parent.split("-");
      if (!parts[1].equals("0".repeat(32)) && !parts[2].equals("0".repeat(16))) {
        trace = parts[1];
        upstream = parts[2];
        flags = parts[3];
      }
    }
    if (legacy != null && (!legacy.matches("[A-Za-z0-9._:-]{1,100}"))) legacy = null;
    if (upstream == null
        && legacy != null
        && legacy.matches("[0-9a-f]{32}")
        && !legacy.equals("0".repeat(32))) trace = legacy;
    return new TraceContext(trace, id().substring(0, 16), upstream, legacy, flags);
  }

  public String header() {
    return "00-" + traceId + "-" + spanId + "-" + flags;
  }

  public String correlationId() {
    return legacyTraceId == null ? traceId : legacyTraceId;
  }

  public void apply(TraceEventVo event) {
    event.setEnvironment(System.getenv().getOrDefault("MSA_ENVIRONMENT", "local"));
    event.setInstanceId(
        System.getenv().getOrDefault("MSA_INSTANCE_ID", "pid-" + ProcessHandle.current().pid()));
    event.setDeploymentVersion(
        System.getenv().getOrDefault("MSA_DEPLOYMENT_VERSION", "development"));
    event.setTraceId(traceId);
    event.setSpanId(spanId);
    event.setParentSpanId(parentSpanId);
    event.setLegacyTraceId(legacyTraceId);
    if (event.getEventId() == null) event.setEventId(UUID.randomUUID().toString());
  }
}
