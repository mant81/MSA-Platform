package com.msa.auth.auth.strategy;

import com.msa.auth.auth.dto.AuthLoginRequestVo;
import com.msa.auth.auth.dto.AuthLoginResponseVo;

public interface AuthStrategy {
    String supportsAuthType();

    AuthLoginResponseVo authenticate(AuthLoginRequestVo request);
}
