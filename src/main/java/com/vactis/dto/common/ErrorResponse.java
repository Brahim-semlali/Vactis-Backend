package com.vactis.dto.common;

import com.vactis.exception.AuthErrorCode;

import com.fasterxml.jackson.annotation.JsonInclude;

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
