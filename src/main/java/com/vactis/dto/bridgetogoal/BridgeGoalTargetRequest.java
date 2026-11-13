package com.vactis.dto.bridgetogoal;

import java.math.BigDecimal;

public record BridgeGoalTargetRequest(String mois, String method, BigDecimal value) {
}