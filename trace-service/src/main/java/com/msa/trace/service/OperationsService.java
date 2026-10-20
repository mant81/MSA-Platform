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
    var rows =
        normalize(
            mapper.selectEvents(
                Math.max(0, cursor),
                Math.max(1, Math.min(limit, 200)) + 1,
                processId,
                businessKey,
                since));
    boolean more = rows.size() > Math.max(1, Math.min(limit, 200));
    if (more) rows = rows.subList(0, rows.size() - 1);
    long next = rows.isEmpty() ? cursor : ((Number) rows.getLast().get("id")).longValue();
    // A timeline page alone cannot prove completeness: query bounds and transport gaps are
    // explicit.
    var projections =
        processId == null
            ? java.util.List.of()
            : jdbc.queryForList("select * from flow_projection where process_id=?", processId);
    return Map.of(
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
