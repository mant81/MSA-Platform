package com.msa.trace.client;

import com.msa.core.TraceEventVo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TraceClient {
    private final RestClient restClient;

    public TraceClient(@Value("${app.trace-service-url:http://localhost:8090}") String traceServiceUrl) {
        this.restClient = RestClient.builder().baseUrl(traceServiceUrl).build();
    }

    public void insert(TraceEventVo vo) {
        restClient.post()
                .uri("/trace-events")
                .body(vo)
                .retrieve()
                .toBodilessEntity();
    }
}
