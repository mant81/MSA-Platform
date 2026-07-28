package com.msa.config.controller;

import com.msa.config.service.ConfigService;
import com.msa.config.vo.ConfigVo;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/configs")
public class ConfigController {
    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    public List<ConfigVo> selectAll() {
        return configService.selectAll();
    }

    @PostMapping
    public int insert(@RequestBody ConfigVo vo) {
        return configService.insert(vo);
    }
}
