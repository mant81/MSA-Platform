package com.msa.auth.controller;

import com.msa.auth.auth.dto.AuthLoginRequestVo;
import com.msa.auth.auth.dto.AuthLoginResponseVo;
import com.msa.auth.auth.dto.AuthSignupRequestVo;
import com.msa.auth.auth.dto.AuthSignupResponseVo;
import com.msa.auth.auth.strategy.AuthStrategy;
import com.msa.auth.auth.strategy.AuthStrategyResolver;
import com.msa.auth.auth.token.JwtTokenService;
import com.msa.auth.service.AuthUserService;
import com.msa.auth.vo.AuthUserVo;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthLoginController {
    private final AuthStrategyResolver authStrategyResolver;
    private final JwtTokenService jwtTokenService;
    private final AuthUserService authUserService;

    public AuthLoginController(AuthStrategyResolver authStrategyResolver, JwtTokenService jwtTokenService, AuthUserService authUserService) {
        this.authStrategyResolver = authStrategyResolver;
        this.jwtTokenService = jwtTokenService;
        this.authUserService = authUserService;
    }

    @PostMapping("/signup")
    public AuthSignupResponseVo signup(@RequestBody AuthSignupRequestVo request) {
        AuthUserVo vo = new AuthUserVo();
        vo.setUserId(request.getUserId());
        vo.setUserName(request.getUserName());
        vo.setPasswordHash(request.getPassword());
        vo.setStatus("ACTIVE");
        authUserService.insert(vo);

        AuthSignupResponseVo response = new AuthSignupResponseVo();
        response.setSuccess(true);
        response.setUserId(request.getUserId());
        response.setAccessToken(jwtTokenService.createAccessToken(request.getUserId(), "PASSWORD"));
        response.setRefreshToken(jwtTokenService.createRefreshToken(request.getUserId(), "PASSWORD"));
        response.setMessage("signup ok");
        return response;
    }

    @PostMapping("/login")
    public AuthLoginResponseVo login(@RequestBody AuthLoginRequestVo request) {
        AuthStrategy strategy = authStrategyResolver.resolve(request.getAuthType());
        AuthUserVo authUser = authUserService.selectByUserIdAndPassword(request.getUserId(), request.getPassword());
        AuthLoginResponseVo response = strategy.authenticate(request);
        if (authUser == null) {
            response.setSuccess(false);
            response.setAccessToken(null);
            response.setRefreshToken(null);
            response.setMessage("invalid userId or password");
            return response;
        }
        response.setAccessToken(jwtTokenService.createAccessToken(request.getUserId(), response.getAuthType()));
        response.setRefreshToken(jwtTokenService.createRefreshToken(request.getUserId(), response.getAuthType()));
        return response;
    }
}
