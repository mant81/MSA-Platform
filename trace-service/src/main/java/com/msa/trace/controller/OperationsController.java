package com.msa.trace.controller;

import com.msa.trace.service.OperationsService;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/operations")
public class OperationsController {
  private final OperationsService service;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public OperationsController(
      OperationsService service, org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.service = service;
    this.jdbc = jdbc;
  }

  @GetMapping("/flows")
  public Map<String, Object> selectFlow(
      @RequestParam(defaultValue = "0") long cursor,
      @RequestParam(defaultValue = "100") int limit,
      @RequestParam(required = false) String processId,
      @RequestParam(required = false) String businessKey,
      @RequestParam(required = false) OffsetDateTime since,
      @RequestParam(required = false) OffsetDateTime until) {
    return service.selectFlow(cursor, limit, processId, businessKey, since, until);
  }

  @GetMapping("/errors/{id}/causes")
  public List<Map<String, Object>> selectCauses(@PathVariable String id) {
    if (!id.matches("[0-9a-f-]{36}"))
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST);
    return jdbc.queryForList(
        "select"
            + " id,error_id,caused_by_error_id,trace_id,process_id,span_id,error_code,source_service"
            + " from trace_event where error_id=? or caused_by_error_id=? order by id limit 100",
        id,
        id);
  }

  @GetMapping("/errors")
  public List<Map<String, Object>> selectErrors(
      @RequestParam(required = false) OffsetDateTime since) {
    return service.selectErrors(since == null ? OffsetDateTime.now().minusDays(1) : since);
  }

  @GetMapping("/errors/{code}/impact")
  public List<Map<String, Object>> selectImpact(
      @PathVariable String code, @RequestParam(defaultValue = "0") long cursor) {
    return service.selectImpact(code, cursor);
  }
}
