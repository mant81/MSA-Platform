package com.msa.auth.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AuthSignupRequestVo {
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9._-]{3,50}")
    private String userId;
    @NotBlank
    @Size(max = 100)
    private String userName;
    @NotBlank
    @Size(min = 12, max = 64)
    private String password;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
