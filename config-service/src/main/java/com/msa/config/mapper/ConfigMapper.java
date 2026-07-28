package com.msa.config.mapper;

import com.msa.core.TraceEventVo;
import com.msa.config.vo.ConfigVo;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConfigMapper {
    List<ConfigVo> selectAll();
    ConfigVo selectByConfigKey(String configKey);
    int insert(ConfigVo vo);
    int insertTraceEvent(TraceEventVo vo);
}
