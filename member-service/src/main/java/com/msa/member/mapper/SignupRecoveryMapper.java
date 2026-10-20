package com.msa.member.mapper;

import com.msa.member.vo.SignupRecoveryVo;
import java.time.OffsetDateTime;
import java.util.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SignupRecoveryMapper {
  List<SignupRecoveryVo> selectRequests(@Param("cursor") String cursor);

  SignupRecoveryVo selectLocked(@Param("key") String key);

  int selectEffects(@Param("memberNo") String memberNo);

  int updateRetryAllowed(@Param("key") String key, @Param("now") OffsetDateTime now);

  int insertAudit(
      @Param("key") String key,
      @Param("actor") String actor,
      @Param("action") String action,
      @Param("reason") String reason,
      @Param("trace") String trace,
      @Param("now") OffsetDateTime now,
      @Param("id") String id);

  List<Map<String, Object>> selectAudit(@Param("key") String key);
}
