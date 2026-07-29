package com.vactis.exception;

import com.vactis.dto.common.ErrorResponse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ErrorResponse> handleAuthException(AuthException ex) {
        log.warn("[AUTH-API] {} | code={} | message={}",
                ex.getStatus(), ex.getCode(), ex.getMessage());

        ErrorResponse body = new ErrorResponse(
                ex.getCode(),
                ex.getMessage(),
                ex.getRemainingAttempts(),
                ex.getMaxAttempts(),
                ex.getLockEndTime() != null ? ex.getLockEndTime().toString() : null,
                ex.getLockMinutes(),
                ex.getLockEndTime() != null
                        ? Math.max(0, java.time.Duration.between(
                                java.time.LocalDateTime.now(), ex.getLockEndTime()).getSeconds())
                        : null);

        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Données invalides");

        log.warn("[AUTH-API] VALIDATION_ERROR | message={}", message);

        return ResponseEntity.badRequest().body(new ErrorResponse(
                AuthErrorCode.VALIDATION_ERROR,
                message,
                null,
                null,
                null,
                null,
                null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("[AUTH-API] Erreur interne", ex);
        return ResponseEntity.internalServerError().body(new ErrorResponse(
                null,
                "Une erreur interne est survenue",
                null,
                null,
                null,
                null,
                null));
    }
}
