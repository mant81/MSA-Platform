package com.msa.test.config;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

@RestControllerAdvice
public class GatewayResponseHandler {
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<byte[]> handleGatewayResponse(RestClientResponseException ex) {
        MediaType contentType = ex.getResponseHeaders() == null ? null : ex.getResponseHeaders().getContentType();
        return ResponseEntity.status(ex.getStatusCode())
                .contentType(contentType == null ? MediaType.APPLICATION_JSON : contentType)
                .body(ex.getResponseBodyAsByteArray());
    }
}
