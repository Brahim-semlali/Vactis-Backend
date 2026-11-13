package com.vactis.exception;

import com.vactis.dto.common.ErrorResponse;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

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

    /**
     * Gère les ResponseStatusException lancées par les services (ex: 404 NOT_FOUND, 400 BAD_REQUEST).
     * Sans ce handler, le @ExceptionHandler(Exception.class) ci-dessous les attraperait
     * et retournerait incorrectement un 500.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        log.warn("[API] {} | {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(new ErrorResponse(
                null,
                ex.getReason() != null ? ex.getReason() : ex.getMessage(),
                null,
                null,
                null,
                null,
                null));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBusinessValidation(IllegalArgumentException ex) {
        String message = ex.getMessage() == null || ex.getMessage().isBlank()
                ? "Les informations saisies ne sont pas valides. Vérifiez les champs et réessayez."
                : ex.getMessage();
        log.warn("[API] Validation métier refusée | {}", message);
        return ResponseEntity.badRequest().body(new ErrorResponse(
                AuthErrorCode.BUSINESS_RULE_VIOLATION, message, null, null, null, null, null));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleBusinessConflict(IllegalStateException ex) {
        String message = ex.getMessage() == null || ex.getMessage().isBlank()
                ? "Cette opération ne peut pas être effectuée dans l’état actuel. Actualisez la page et réessayez."
                : ex.getMessage();
        log.warn("[API] Conflit métier | {}", message);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
                AuthErrorCode.DATA_CONFLICT, message, null, null, null, null, null));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataConflict(DataIntegrityViolationException ex) {
        log.warn("[API] Écriture refusée par une contrainte de données", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
                AuthErrorCode.DATA_CONFLICT,
                "L’enregistrement a été refusé car certaines données ne sont pas compatibles. Vérifiez les informations et réessayez; si le problème persiste, contactez l’administrateur.",
                null, null, null, null, null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String message = "Format JSON invalide. Vérifiez les champs envoyés.";
        if (cause instanceof JsonParseException jsonParseException) {
            message = "Format JSON invalide : " + jsonParseException.getOriginalMessage();
        } else if (cause instanceof InvalidFormatException invalidFormatException
                && invalidFormatException.getTargetType().isEnum()) {
            String field = invalidFormatException.getPath().stream()
                    .map(JsonMappingException.Reference::getFieldName)
                    .filter(name -> name != null && !name.isBlank())
                    .reduce((first, second) -> second)
                    .orElse("champ");
            String allowedValues = java.util.Arrays.stream(invalidFormatException.getTargetType().getEnumConstants())
                    .map(value -> ((Enum<?>) value).name())
                    .collect(java.util.stream.Collectors.joining(", "));
            message = "Valeur « " + invalidFormatException.getValue() + " » invalide pour le champ « " + field + " »"
                    + ". Valeurs autorisées : " + allowedValues + ".";
        }
        log.warn("[API] Requête JSON invalide | {}", message, ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(
                com.vactis.exception.AuthErrorCode.INVALID_JSON,
                message,
                null,
                null,
                null,
                null,
                null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("[API] Erreur interne inattendue", ex);
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
