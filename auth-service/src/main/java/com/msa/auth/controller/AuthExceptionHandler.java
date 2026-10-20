package com.msa.auth.controller;

import com.msa.core.BusinessException;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class AuthExceptionHandler {
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<?> duplicate() { return error(409, "USER_ALREADY_EXISTS"); }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class, BusinessException.class})
    public ResponseEntity<?> validation() { return error(400, "INVALID_REQUEST"); }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> status(ResponseStatusException ex) { return error(ex.getStatusCode().value(), "INVALID_REQUEST"); }
    private ResponseEntity<?> error(int status, String code) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(Map.of("success", false, "code", code));
    }
}
