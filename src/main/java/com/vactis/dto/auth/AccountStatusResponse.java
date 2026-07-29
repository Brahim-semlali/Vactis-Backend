package com.vactis.dto.auth;


import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccountStatusResponse(
        boolean suspended,
        String lockedUntil,
        Integer lockMinutes,
        int failedLoginAttempts,
        int maxAttempts,
        int remainingAttempts,
        long remainingSeconds
) {}
