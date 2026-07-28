package com.msa.trace.controller;

import com.msa.core.TraceEventVo;
import com.msa.trace.service.TraceService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
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
    public int insert(@RequestBody TraceEventVo vo) {
        return traceService.insert(vo);
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
    public List<TraceEventVo> selectByProcessIdAndTraceId(@PathVariable String processId, @PathVariable String traceId) {
        return traceService.selectByProcessIdAndTraceId(processId, traceId);
    }

    @GetMapping("/status/{status}")
    public List<TraceEventVo> selectByStatus(@PathVariable String status) {
        return traceService.selectByStatus(status);
    }
}
