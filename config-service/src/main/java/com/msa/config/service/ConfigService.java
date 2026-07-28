package com.msa.config.service;

import com.msa.config.mapper.ConfigMapper;
import com.msa.config.vo.ConfigVo;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ConfigService {
    private final ConfigMapper configMapper;

    public ConfigService(ConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    public List<ConfigVo> selectAll() {
        return configMapper.selectAll();
    }

    public ConfigVo selectByConfigKey(String configKey) {
        return configMapper.selectByConfigKey(configKey);
    }

    public int insert(ConfigVo vo) {
        return configMapper.insert(vo);
    }
}
