package com.msa.auth.auth.token;

import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    public String createAccessToken(String userId, String authType) {
        return "jwt-access-token:" + authType + ":" + userId;
    }

    public String createRefreshToken(String userId, String authType) {
        return "jwt-refresh-token:" + authType + ":" + userId;
    }

    public boolean isValidAccessToken(String token) {
        return token != null && token.startsWith("jwt-access-token:");
    }
}
