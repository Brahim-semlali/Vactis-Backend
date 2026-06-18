package com.vactis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vactis.exception.AuthErrorCode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        AuthErrorCode code,
        String message,
        Integer remainingAttempts,
        Integer maxAttempts,
        String lockedUntil,
        Integer lockMinutes,
        Long remainingSeconds
) {}
