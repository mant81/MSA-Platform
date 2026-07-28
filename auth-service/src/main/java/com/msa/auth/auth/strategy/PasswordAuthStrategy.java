package com.msa.auth.auth.strategy;

import com.msa.auth.auth.dto.AuthLoginRequestVo;
import com.msa.auth.auth.dto.AuthLoginResponseVo;
import org.springframework.stereotype.Component;

@Component
public class PasswordAuthStrategy implements AuthStrategy {

    @Override
    public String supportsAuthType() {
        return "PASSWORD";
    }

    @Override
    public AuthLoginResponseVo authenticate(AuthLoginRequestVo request) {
        AuthLoginResponseVo response = new AuthLoginResponseVo();
        response.setSuccess(true);
        response.setAuthType(supportsAuthType());
        response.setTokenType("Bearer");
        response.setAccessToken("access-token-placeholder");
        response.setRefreshToken("refresh-token-placeholder");
        response.setExpiresIn(3600L);
        response.setMessage("password auth ok");
        return response;
    }
}
