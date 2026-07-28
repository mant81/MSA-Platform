package com.msa.config.config;

import com.msa.core.TraceEventType;
import com.msa.core.TraceEventVo;
import com.msa.config.mapper.ConfigMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TraceFilter extends OncePerRequestFilter {

    private final RestClient restClient;

    public TraceFilter(@Value("${app.trace-service-url:http://localhost:8090}") String traceServiceUrl) {
        this.restClient = RestClient.builder().baseUrl(traceServiceUrl).build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String traceId = getOrCreateTraceId(request);
        response.setHeader("X-Trace-Id", traceId);
        insertTrace(traceId, request, TraceEventType.REQUEST_STARTED.name(), "RUNNING", 0L, null, null);
        try {
            filterChain.doFilter(request, response);
            insertTrace(traceId, request, TraceEventType.REQUEST_COMPLETED.name(), "SUCCESS", System.currentTimeMillis() - start, null, null);
        } catch (Exception ex) {
            insertTrace(traceId, request, TraceEventType.REQUEST_FAILED.name(), "FAILED", System.currentTimeMillis() - start, "INTERNAL", ex.getMessage());
            throw ex;
        }
    }

    private void insertTrace(String traceId, HttpServletRequest request, String eventType, String status, Long elapsedMs, String errorCode, String errorMessage) {
        TraceEventVo trace = new TraceEventVo();
        trace.setTraceId(traceId);
        trace.setEventType(eventType);
        trace.setHttpMethod(request.getMethod());
        trace.setPath(request.getRequestURI());
        trace.setStatus(status);
        trace.setElapsedMs(elapsedMs);
        trace.setErrorCode(errorCode);
        trace.setErrorMessage(errorMessage);
        trace.setCreatedAt(OffsetDateTime.now());
        postTrace(trace);
    }

    private String getOrCreateTraceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().replace("-", "").substring(0, 16)
                : traceId;
    }

    private void postTrace(TraceEventVo trace) {
        restClient.post()
                .uri("/trace-events")
                .body(trace)
                .retrieve()
                .toBodilessEntity();
    }
}
