package com.msa.core.call;

public enum CallErrorPolicy {
    FAIL_FAST,
    RETRY,
    FALLBACK,
    IGNORE
}
