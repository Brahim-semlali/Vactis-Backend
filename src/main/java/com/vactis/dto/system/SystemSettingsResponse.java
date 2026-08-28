package com.vactis.dto.system;

import java.time.LocalDateTime;

public record SystemSettingsResponse(
        Integer dureeSessionMinutes,
        Integer dureeInactiviteJours,
        Integer mdpLongueurMinimale,
        Boolean mdpExigeMajuscule,
        Boolean mdpExigeChiffre,
        Boolean mdpExigeCaractereSpecial,
        Integer mdpExpirationJours,
        Integer maxTentativesConnexion,
        Integer dureeBlocageMinutes,
        Boolean journalConnexionActif,
        LocalDateTime updatedAt,
        String updatedBy
) {}