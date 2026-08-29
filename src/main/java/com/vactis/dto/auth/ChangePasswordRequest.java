package com.vactis.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank(message = "Le mot de passe actuel est obligatoire") String currentPassword,
        @NotBlank(message = "Le nouveau mot de passe est obligatoire") String newPassword
) {}