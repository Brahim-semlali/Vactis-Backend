package com.vactis.dto.auth;

public record UserProfileResponse(
        Long id,
        String username,
        String firstName,
        String lastName,
        String email,
        String phone,
        String avatar,
        String role
) {}
