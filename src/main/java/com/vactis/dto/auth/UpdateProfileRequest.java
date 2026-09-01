package com.vactis.dto.auth;

import jakarta.validation.constraints.Email;

public record UpdateProfileRequest(
        String firstName,
        String lastName,
        @Email(message = "L'adresse email n'est pas valide")
        String email,
        String phone,
        String avatar
) {}
