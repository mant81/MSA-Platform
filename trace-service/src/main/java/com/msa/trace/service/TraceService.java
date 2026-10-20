package com.msa.trace.service;

import com.msa.core.TraceEventVo;
import com.msa.trace.mapper.TraceEventMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TraceService {
  private final TraceEventMapper traceEventMapper;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  private final org.springframework.transaction.support.TransactionTemplate tx;

  public TraceService(
      TraceEventMapper traceEventMapper,
      org.springframework.jdbc.core.JdbcTemplate jdbc,
      org.springframework.transaction.PlatformTransactionManager manager) {
    this.jdbc = jdbc;
    this.tx = new org.springframework.transaction.support.TransactionTemplate(manager);
    this.traceEventMapper = traceEventMapper;
  }

  public int insert(TraceEventVo vo) {
    if (vo.getTraceId() == null
        || !vo.getTraceId().matches("[A-Za-z0-9._:-]{1,100}")
        || vo.getEventType() == null
        || !vo.getEventType().matches("[A-Z_]{1,50}")
        || (vo.getEventId() != null && !vo.getEventId().matches("[0-9a-f-]{36}")))
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid trace event");
    if (vo.getErrorCode() != null) {
      if (!vo.getErrorCode().matches("[A-Z][A-Z0-9_]{0,99}"))
        throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid error code");
      if (vo.getErrorId() == null) vo.setErrorId(java.util.UUID.randomUUID().toString());
      vo.setErrorGroupId(vo.getSourceService() + ":" + vo.getErrorCode());
    }
    if (vo.getCreatedAt() == null) vo.setCreatedAt(java.time.OffsetDateTime.now());
    if (vo.getEnvironment() == null) vo.setEnvironment("legacy");
    if (vo.getInstanceId() == null) vo.setInstanceId("legacy-unknown");
    if (vo.getDeploymentVersion() == null) vo.setDeploymentVersion("legacy-unknown");
    // Publisher-provided text is not a safe operational error description.
    if (vo.getErrorCode() != null)
      vo.setErrorMessage("Operation failed: " + vo.getErrorCode().replaceAll("[^A-Z0-9_]", ""));
    try {
      return tx.execute(
          status -> {
            int inserted = traceEventMapper.insert(vo);
            project(vo);
            projectStep(vo);
            return inserted;
          });
    } catch (org.springframework.dao.DuplicateKeyException duplicate) {
      if (vo.getEventId() != null
          && jdbc.queryForObject(
                  "select count(*) from trace_event where source_service=? and event_id=?",
                  Integer.class,
                  vo.getSourceService(),
                  vo.getEventId())
              > 0) return 0;
      throw duplicate; // projection race rolled back event too; exporter must retry.
    }
  }

  private void project(TraceEventVo event) {
    if (!"member-service".equals(event.getSourceService())
        || event.getProcessId() == null
        || event.getAggregateVersion() == null
        || !java.util.Set.of("REQUEST_STARTED", "REQUEST_COMPLETED", "REQUEST_FAILED")
            .contains(event.getEventType())) return;
    String state =
        "REQUEST_STARTED".equals(event.getEventType())
            ? "RUNNING"
            : "REQUEST_COMPLETED".equals(event.getEventType()) ? "SUCCEEDED" : "FAILED";
    int changed =
        jdbc.update(
            "update flow_projection set status=?,aggregate_version=?,last_event_id=?,updated_at=?"
                + " where source_service=? and process_id=? and aggregate_version < ? and"
                + " status='RUNNING'",
            state,
            event.getAggregateVersion(),
            event.getEventId(),
            java.time.OffsetDateTime.now(),
            event.getSourceService(),
            event.getProcessId(),
            event.getAggregateVersion());
    if (changed == 0
        && jdbc.queryForObject(
                "select count(*) from flow_projection where source_service=? and process_id=?",
                Integer.class,
                event.getSourceService(),
                event.getProcessId())
            == 0)
      jdbc.update(
          "insert into"
              + " flow_projection(source_service,process_id,trace_id,business_key,status,aggregate_version,last_event_id,updated_at)"
              + " values (?,?,?,?,?,?,?,?)",
          event.getSourceService(),
          event.getProcessId(),
          event.getTraceId(),
          event.getBusinessKey(),
          state,
          event.getAggregateVersion(),
          event.getEventId(),
          java.time.OffsetDateTime.now());
  }

  private void projectStep(TraceEventVo event) {
    if (!"member-service".equals(event.getSourceService())
        || event.getProcessId() == null
        || event.getStepId() == null
        || event.getStepExecutionId() == null
        || event.getAttempt() == null
        || event.getAttempt() < 1
        || event.getAggregateVersion() == null) return;
    String state =
        switch (event.getEventType()) {
          case "STEP_STARTED" -> "RUNNING";
          case "STEP_SUCCEEDED" -> "SUCCEEDED";
          case "STEP_FAILED" -> "TIMEOUT".equals(event.getStatus()) ? "TIMED_OUT" : "FAILED";
          case "STEP_NOT_EXECUTED" -> "NOT_EXECUTED";
          default -> null;
        };
    if (state == null) return;
    int changed =
        jdbc.update(
            "update flow_step_projection set"
                + " status=?,aggregate_version=?,last_event_id=?,updated_at=? where"
                + " source_service=? and process_id=? and step_id=? and step_execution_id=? and"
                + " attempt=? and aggregate_version < ? and status='RUNNING'",
            state,
            event.getAggregateVersion(),
            event.getEventId(),
            java.time.OffsetDateTime.now(),
            event.getSourceService(),
            event.getProcessId(),
            event.getStepId(),
            event.getStepExecutionId(),
            event.getAttempt(),
            event.getAggregateVersion());
    if (changed == 0
        && jdbc.queryForObject(
                "select count(*) from flow_step_projection where source_service=? and process_id=?"
                    + " and step_execution_id=?",
                Integer.class,
                event.getSourceService(),
                event.getProcessId(),
                event.getStepExecutionId())
            == 0)
      jdbc.update(
          "insert into flow_step_projection(source_service,process_id,step_id,step_execution_id,"
              + "attempt,trace_id,span_id,status,aggregate_version,last_event_id,updated_at)"
              + " values(?,?,?,?,?,?,?,?,?,?,?)",
          event.getSourceService(),
          event.getProcessId(),
          event.getStepId(),
          event.getStepExecutionId(),
          event.getAttempt(),
          event.getTraceId(),
          event.getSpanId(),
          state,
          event.getAggregateVersion(),
          event.getEventId(),
          java.time.OffsetDateTime.now());
  }

  public List<TraceEventVo> selectAll() {
    return traceEventMapper.selectAll();
  }

  public List<TraceEventVo> selectByTraceId(String traceId) {
    return traceEventMapper.selectByTraceId(traceId);
  }

  public List<TraceEventVo> selectByProcessId(String processId) {
    return traceEventMapper.selectByProcessId(processId);
  }

  public List<TraceEventVo> selectByProcessIdAndTraceId(String processId, String traceId) {
    return traceEventMapper.selectByProcessIdAndTraceId(processId, traceId);
  }

  public List<TraceEventVo> selectByStatus(String status) {
    return traceEventMapper.selectByStatus(status);
  }
}
