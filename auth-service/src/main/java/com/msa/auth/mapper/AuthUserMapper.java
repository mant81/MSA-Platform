package com.msa.auth.mapper;

import com.msa.core.TraceEventVo;
import com.msa.auth.vo.AuthUserVo;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthUserMapper {
    List<AuthUserVo> selectAll();
    AuthUserVo selectByUserId(String userId);
    int insert(AuthUserVo vo);
    int insertTraceEvent(TraceEventVo vo);
}
