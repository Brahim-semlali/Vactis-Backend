package com.vactis.dto.bridgetogoal;

public record BridgeOpportunityItem(
        String type,
        String label,
        String segment,
        int medecins,
        long caReference,
        long potentielTheorique,
        String description
) {}