package com.msa.trace.controller;

import com.msa.core.TraceEventVo;
import com.msa.trace.service.TraceService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/trace-events")
public class TraceController {
  private final TraceService traceService;

  public TraceController(TraceService traceService) {
    this.traceService = traceService;
  }

  @PostMapping
  public int insert(
      @RequestBody TraceEventVo vo, @RequestAttribute("authenticatedServiceId") String source) {
    vo.setSourceService(source);
    return traceService.insert(vo);
  }

  @PostMapping("/batch")
  public List<com.msa.core.BatchAckVo> insertBatch(
      @RequestBody List<TraceEventVo> events,
      @RequestAttribute("authenticatedServiceId") String source) {
    if (events.isEmpty() || events.size() > 100)
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "Batch must contain 1..100 events");
    var acknowledgements = new java.util.ArrayList<com.msa.core.BatchAckVo>();
    for (var event : events) {
      if (event == null || event.getEventId() == null)
        throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.BAD_REQUEST, "Batch requires event IDs");
      event.setSourceService(source);
      int count = traceService.insert(event);
      acknowledgements.add(
          new com.msa.core.BatchAckVo(event.getEventId(), count == 0 ? "DUPLICATE" : "PERSISTED"));
    }
    return acknowledgements;
  }

  @GetMapping
  public List<TraceEventVo> selectAll() {
    return traceService.selectAll();
  }

  @GetMapping("/trace-id/{traceId}")
  public List<TraceEventVo> selectByTraceId(@PathVariable String traceId) {
    return traceService.selectByTraceId(traceId);
  }

  @GetMapping("/process-id/{processId}")
  public List<TraceEventVo> selectByProcessId(@PathVariable String processId) {
    return traceService.selectByProcessId(processId);
  }

  @GetMapping("/process-id/{processId}/trace-id/{traceId}")
  public List<TraceEventVo> selectByProcessIdAndTraceId(
      @PathVariable String processId, @PathVariable String traceId) {
    return traceService.selectByProcessIdAndTraceId(processId, traceId);
  }

  @GetMapping("/status/{status}")
  public List<TraceEventVo> selectByStatus(@PathVariable String status) {
    return traceService.selectByStatus(status);
  }
}
