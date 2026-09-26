package com.vactis.controller;

import com.vactis.dto.bridgetogoal.BridgeToGoalResponse;
import com.vactis.service.BridgeToGoalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Contrôleur REST pour la modélisation budgétaire Bridge to Goal (Sections 12 & 13).
 */
@RestController
@RequestMapping("/api/bridge-to-goal")
@RequiredArgsConstructor
public class BridgeToGoalController {

    private final BridgeToGoalService bridgeToGoalService;

    @GetMapping
    public ResponseEntity<BridgeToGoalResponse> getBridgeToGoal(
            @RequestParam(required = false) Long target,
            @RequestParam(required = false) String mois
    ) {
        return ResponseEntity.ok(bridgeToGoalService.simulerTrajectoire(target, mois));
    }
}
