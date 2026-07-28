package com.msa.trace.service;

import com.msa.core.TraceEventVo;
import com.msa.trace.mapper.TraceEventMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TraceService {
    private final TraceEventMapper traceEventMapper;

    public TraceService(TraceEventMapper traceEventMapper) {
        this.traceEventMapper = traceEventMapper;
    }

    public int insert(TraceEventVo vo) {
        return traceEventMapper.insert(vo);
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
