package com.msa.trace.mapper;

import com.msa.core.TraceEventVo;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TraceEventMapper {
    int insert(TraceEventVo vo);
    List<TraceEventVo> selectAll();
    List<TraceEventVo> selectByTraceId(String traceId);
    List<TraceEventVo> selectByProcessId(String processId);
    List<TraceEventVo> selectByProcessIdAndTraceId(String processId, String traceId);
    List<TraceEventVo> selectByStatus(String status);
}
