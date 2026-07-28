package com.msa.auth.auth.strategy;

import com.msa.core.BusinessException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AuthStrategyResolver {
    private final List<AuthStrategy> strategies;

    public AuthStrategyResolver(List<AuthStrategy> strategies) {
        this.strategies = strategies;
    }

    public AuthStrategy resolve(String authType) {
        String normalized = authType == null || authType.isBlank() ? "PASSWORD" : authType.trim().toUpperCase();
        for (AuthStrategy strategy : strategies) {
            if (strategy.supportsAuthType().equals(normalized)) {
                return strategy;
            }
        }
        throw new BusinessException("AUTH_TYPE_NOT_SUPPORTED", "Unsupported auth type: " + normalized);
    }
}
