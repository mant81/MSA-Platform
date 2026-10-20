package com.msa.trace.mapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OperationsMapper {
  List<Map<String, Object>> selectEvents(
      @Param("cursor") long cursor,
      @Param("limit") int limit,
      @Param("processId") String processId,
      @Param("businessKey") String businessKey,
      @Param("since") OffsetDateTime since);

  List<Map<String, Object>> selectErrors(@Param("since") OffsetDateTime since);

  List<Map<String, Object>> selectErrorImpact(
      @Param("code") String code, @Param("cursor") long cursor);
}
