package com.msa.core.call;

public final class CallConvention {

    public static final String TRACE_HEADER = "X-Trace-Id";
    public static final String DEFAULT_TIMEOUT_MS = "3000";
    public static final String DEFAULT_RETRY_COUNT = "3";
    public static final String INTERNAL_CALL_POLICY = "Internal synchronous calls should use Feign Client.";
    public static final String EXTERNAL_CALL_POLICY = "External API calls should use RestClient or WebClient.";
    public static final String TRACE_POLICY = "Every call must preserve and record X-Trace-Id.";
    public static final String INTERNAL_PACKAGE = "com.msa.*.client.internal";
    public static final String EXTERNAL_PACKAGE = "com.msa.*.client.external";

    private CallConvention() {
    }
}
