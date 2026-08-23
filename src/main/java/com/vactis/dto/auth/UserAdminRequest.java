package com.vactis.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserAdminRequest(
        @NotBlank(message = "Le nom d'utilisateur est obligatoire")
        String username,
        @Size(min = 6, message = "Le mot de passe doit contenir au moins 6 caractères")
        String password,
        @NotBlank(message = "Le prénom est obligatoire")
        String firstName,
        @NotBlank(message = "Le nom est obligatoire")
        String lastName,
        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "L'email n'est pas valide")
        String email,
        String phone,
        Boolean enabled
) {}
