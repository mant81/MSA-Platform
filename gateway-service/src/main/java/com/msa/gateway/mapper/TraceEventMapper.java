package com.msa.gateway.mapper;

import com.msa.core.TraceEventVo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TraceEventMapper {
    int insert(TraceEventVo vo);
}
