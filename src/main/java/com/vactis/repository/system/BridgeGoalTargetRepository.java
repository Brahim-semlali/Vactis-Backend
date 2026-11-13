package com.vactis.repository.system;

import com.vactis.model.system.BridgeGoalTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BridgeGoalTargetRepository extends JpaRepository<BridgeGoalTarget, Long> {
    Optional<BridgeGoalTarget> findByPeriod(String period);
}