package com.vactis.dto.bridgetogoal;

import java.math.BigDecimal;

public record BridgeGoalTargetResponse(
    String mois,
    String method,
    BigDecimal value,
    Long previousMonthRevenue,
    Long targetAmount
) {
}