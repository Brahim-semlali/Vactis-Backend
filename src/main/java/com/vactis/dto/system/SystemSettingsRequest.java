package com.vactis.dto.system;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SystemSettingsRequest(
        @NotNull(message = "La durée de session est obligatoire")
        @Positive(message = "La durée de session doit être un entier strictement positif")
        Integer dureeSessionMinutes,

        @NotNull(message = "La durée d'inactivité est obligatoire")
        @Positive(message = "La durée d'inactivité doit être un entier strictement positif")
        Integer dureeInactiviteJours,

        @NotNull @Positive Integer mdpLongueurMinimale,
        @NotNull Boolean mdpExigeMajuscule,
        @NotNull Boolean mdpExigeChiffre,
        @NotNull Boolean mdpExigeCaractereSpecial,
        @NotNull @jakarta.validation.constraints.PositiveOrZero Integer mdpExpirationJours,
        @NotNull @Positive Integer maxTentativesConnexion,
        @NotNull @jakarta.validation.constraints.PositiveOrZero Integer dureeBlocageMinutes,
        @NotNull Boolean journalConnexionActif
) {}