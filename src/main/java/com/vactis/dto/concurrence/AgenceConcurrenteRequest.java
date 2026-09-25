package com.vactis.dto.concurrence;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AgenceConcurrenteRequest(
        @NotBlank(message = "Le nom de l'agence est obligatoire")
        String nom,

        String enseigne,

        @NotNull(message = "La latitude est obligatoire")
        @DecimalMin(value = "-90.0", message = "La latitude doit être supérieure ou égale à -90")
        @DecimalMax(value = "90.0", message = "La latitude doit être inférieure ou égale à 90")
        Double latitude,

        @NotNull(message = "La longitude est obligatoire")
        @DecimalMin(value = "-180.0", message = "La longitude doit être supérieure ou égale à -180")
        @DecimalMax(value = "180.0", message = "La longitude doit être inférieure ou égale à 180")
        Double longitude,

        String adresse,

        String notes
) {}
