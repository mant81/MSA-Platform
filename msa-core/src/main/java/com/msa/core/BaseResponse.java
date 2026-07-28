package com.msa.core;

public class BaseResponse {
    private final String timestamp;
    private final String traceId;
    private final String path;
    private final boolean success;
    private final String code;
    private final String message;

    public BaseResponse(String timestamp, String traceId, String path, boolean success, String code, String message) {
        this.timestamp = timestamp;
        this.traceId = traceId;
        this.path = path;
        this.success = success;
        this.code = code;
        this.message = message;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getPath() {
        return path;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
