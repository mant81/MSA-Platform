package com.msa.auth.controller;

import com.msa.auth.auth.dto.AuthLoginRequestVo;
import com.msa.auth.auth.dto.AuthLoginResponseVo;
import com.msa.auth.auth.dto.AuthSignupRequestVo;
import com.msa.auth.auth.dto.AuthSignupResponseVo;
import com.msa.auth.auth.strategy.AuthStrategyResolver;
import com.msa.auth.service.AuthUserService;
import com.msa.auth.service.RefreshTokenService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthLoginController {
    private final AuthStrategyResolver strategies;
    private final AuthUserService users;
    private final RefreshTokenService sessions;
    public AuthLoginController(AuthStrategyResolver strategies, AuthUserService users, RefreshTokenService sessions) {
        this.strategies = strategies;this.users = users;this.sessions = sessions;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthSignupResponseVo> signup(@Valid @RequestBody AuthSignupRequestVo request) {
        var tokens = sessions.register(request.getUserId(), request.getUserName(), request.getPassword());
        AuthSignupResponseVo response = new AuthSignupResponseVo();response.setSuccess(true);
        response.setUserId(request.getUserId());response.setAccessToken(tokens.getAccessToken());
        response.setRefreshToken(tokens.getRefreshToken());response.setMessage("signup ok");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache").body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthLoginResponseVo> login(@Valid @RequestBody AuthLoginRequestVo request) {
        strategies.resolve(request.getAuthType());
        var user = users.selectByUserIdAndPassword(request.getUserId(), request.getPassword());
        if (user == null) {
            AuthLoginResponseVo response = new AuthLoginResponseVo();response.setSuccess(false);
            response.setCode("LOGIN_INVALID");response.setMessage("Invalid user ID or password");
            return tokenResponse(response);
        }
        return tokenResponse(sessions.issue(user));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthLoginResponseVo> refresh(@RequestBody Map<String, String> request) {
        return tokenResponse(sessions.rotate(request.get("refreshToken")));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody Map<String, String> request) {
        sessions.logout(request.get("refreshToken"));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private ResponseEntity<AuthLoginResponseVo> tokenResponse(AuthLoginResponseVo response) {
        return ResponseEntity.status(response.isSuccess() ? 200 : 401).cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache").body(response);
    }
}
