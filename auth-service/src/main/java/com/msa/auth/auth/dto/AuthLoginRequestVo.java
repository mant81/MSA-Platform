package com.msa.auth.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AuthLoginRequestVo {
    private String authType;
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9._-]{3,50}")
    private String userId;
    @NotBlank
    @Size(max = 72)
    private String password;

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
