package com.msa.member.client;

import com.msa.core.TraceEventVo;
import com.msa.core.security.ServiceCredentials;
import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class TraceClient {
    private static final Logger log = LoggerFactory.getLogger(TraceClient.class);
    private final RestClient restClient;

    public TraceClient(@Value("${app.trace-service-url:http://localhost:8090}") String traceServiceUrl, ServiceCredentials credentials) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(500)).build());
        factory.setReadTimeout(Duration.ofMillis(500));
        this.restClient = RestClient.builder().baseUrl(traceServiceUrl).requestFactory(factory).defaultHeaders(headers -> credentials.apply(headers, "trace-service")).build();
    }

    public void insert(TraceEventVo vo) {
        try {
            restClient.post().uri("/trace-events").body(vo).retrieve().toBodilessEntity();
        } catch (RestClientException ex) {
            log.warn("Member Trace delivery failed; eventType={}", vo.getEventType());
        }
    }
}
