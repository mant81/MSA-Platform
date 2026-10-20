package com.msa.core.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(Exception.class)
  public ResponseEntity<?> error(Exception error, HttpServletRequest request) {
    int status =
        error instanceof org.springframework.web.ErrorResponse e
            ? e.getStatusCode().value()
            : error instanceof org.springframework.web.bind.MethodArgumentNotValidException
                    || error
                        instanceof
                        org.springframework.http.converter.HttpMessageNotReadableException
                ? 400
                : error instanceof org.springframework.dao.DuplicateKeyException
                    ? 409
                    : error instanceof org.springframework.dao.DataAccessException ? 503 : 500;
    String category =
        status == 400
            ? "VALIDATION"
            : status == 409 ? "BUSINESS" : status == 503 ? "DATA" : "INTERNAL";
    String id = UUID.randomUUID().toString();
    request.setAttribute("errorId", id);
    return ResponseEntity.status(status)
        .header("Cache-Control", "no-store")
        .header("X-Error-Id", id)
        .body(
            Map.of(
                "success",
                false,
                "code",
                "HTTP_" + status,
                "errorId",
                id,
                "category",
                category,
                "traceId",
                Objects.toString(request.getAttribute("traceId"), ""),
                "message",
                "Request could not be completed"));
  }
}
