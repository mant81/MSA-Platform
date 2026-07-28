package com.msa.auth.controller;

import com.msa.auth.service.AuthUserService;
import com.msa.auth.vo.AuthUserVo;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class AuthUserController {
    private final AuthUserService authUserService;

    public AuthUserController(AuthUserService authUserService) {
        this.authUserService = authUserService;
    }

    @GetMapping
    public List<AuthUserVo> selectAll() {
        return authUserService.selectAll();
    }

    @PostMapping
    public int insert(@RequestBody AuthUserVo vo) {
        return authUserService.insert(vo);
    }
}
