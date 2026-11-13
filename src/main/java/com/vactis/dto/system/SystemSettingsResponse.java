package com.vactis.dto.system;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SystemSettingsResponse(
        Integer dureeSessionMinutes,
        Integer dureeInactiviteJours,
        Integer mdpLongueurMinimale,
        Boolean mdpExigeMajuscule,
        Boolean mdpExigeChiffre,
        Boolean mdpExigeCaractereSpecial,
        Integer maxTentativesConnexion,
        Integer dureeBlocageMinutes,
        Boolean journalConnexionActif,
        Long bridgeGoalTarget,
        String bridgeGoalMethod,
        BigDecimal bridgeGoalInputValue,
        Long bridgeGoalReferenceRevenue,
        LocalDateTime updatedAt,
        String updatedBy
) {}