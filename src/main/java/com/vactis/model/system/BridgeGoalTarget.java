package com.vactis.model.system;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bridge_goal_targets", uniqueConstraints =
    @UniqueConstraint(name = "uk_bridge_goal_targets_period", columnNames = "period"))
@Getter
@Setter
@NoArgsConstructor
public class BridgeGoalTarget {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "period", nullable = false, length = 7)
    private String period;

    @Column(name = "method", nullable = false, length = 20)
    private String method;

    @Column(name = "input_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal inputValue;

    @Column(name = "previous_month_revenue", nullable = false)
    private Long previousMonthRevenue;

    @Column(name = "target_amount", nullable = false)
    private Long targetAmount;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public BridgeGoalTarget(String period, String method, BigDecimal inputValue,
                            Long previousMonthRevenue, Long targetAmount) {
        this.period = period;
        this.method = method;
        this.inputValue = inputValue;
        this.previousMonthRevenue = previousMonthRevenue;
        this.targetAmount = targetAmount;
        this.updatedAt = LocalDateTime.now();
    }
}