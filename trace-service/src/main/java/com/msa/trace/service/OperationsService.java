package com.msa.trace.service;

import com.msa.trace.mapper.OperationsMapper;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class OperationsService {
  private final OperationsMapper mapper;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public OperationsService(
      OperationsMapper mapper, org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.mapper = mapper;
    this.jdbc = jdbc;
  }

  private List<Map<String, Object>> normalize(List<Map<String, Object>> rows) {
    return rows.stream()
        .map(
            row -> {
              Map<String, Object> clean = new LinkedHashMap<>();
              row.forEach((k, v) -> clean.put(k.toLowerCase(Locale.ROOT), v));
              return clean;
            })
        .toList();
  }

  public Map<String, Object> selectFlow(
      long cursor, int limit, String processId, String businessKey, OffsetDateTime since) {
    return selectFlow(cursor, limit, processId, businessKey, since, null);
  }

  public Map<String, Object> selectFlow(
      long cursor,
      int limit,
      String processId,
      String businessKey,
      OffsetDateTime since,
      OffsetDateTime until) {
    if (since != null && until != null && !since.isBefore(until))
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "since must precede until");
    var rows =
        normalize(
            mapper.selectEvents(
                Math.max(0, cursor),
                Math.max(1, Math.min(limit, 200)) + 1,
                processId,
                businessKey,
                since,
                until));
    boolean more = rows.size() > Math.max(1, Math.min(limit, 200));
    if (more) rows = rows.subList(0, rows.size() - 1);
    long next = rows.isEmpty() ? cursor : ((Number) rows.getLast().get("id")).longValue();
    // A timeline page alone cannot prove completeness: query bounds and transport gaps are
    // explicit.
    var projections =
        processId == null
            ? java.util.List.of()
            : jdbc.queryForList("select * from flow_projection where process_id=?", processId);
    var steps =
        processId == null
            ? List.<Map<String, Object>>of()
            : normalize(
                jdbc.queryForList(
                    "select * from flow_step_projection where process_id=? order by"
                        + " step_id,attempt,step_execution_id limit 201",
                    processId));
    boolean stepsTruncated = steps.size() > 200;
    if (stepsTruncated) steps = steps.subList(0, 200);
    return Map.of(
        "steps",
        steps,
        "completenessDetails",
        Map.of(
            "scope",
            "retained-member-business-events",
            "reason",
            processId == null ? "PROCESS_NOT_SELECTED" : "EXPECTED_EVENT_MANIFEST_UNAVAILABLE",
            "stepsTruncated",
            stepsTruncated,
            "projectionScope",
            "all-ingested-events-for-process",
            "eventPageFiltered",
            since != null || until != null || businessKey != null),
        "projections",
        projections,
        "events",
        rows,
        "nextCursor",
        next,
        "hasMore",
        more,
        "completeness",
        "UNVERIFIED",
        "ordering",
        "ingestion-id");
  }

  public List<Map<String, Object>> selectErrors(OffsetDateTime since) {
    return normalize(mapper.selectErrors(since));
  }

  public List<Map<String, Object>> selectImpact(String code, long cursor) {
    return normalize(mapper.selectErrorImpact(code, Math.max(0, cursor)));
  }
}
