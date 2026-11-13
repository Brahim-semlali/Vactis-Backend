package com.vactis.controller;

import com.vactis.dto.bridgetogoal.BridgeToGoalResponse;
import com.vactis.dto.bridgetogoal.BridgeGoalTargetRequest;
import com.vactis.dto.bridgetogoal.BridgeGoalTargetResponse;
import com.vactis.service.BridgeGoalTargetService;
import com.vactis.service.BridgeToGoalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Contrôleur REST pour la modélisation budgétaire Bridge to Goal (Sections 12 & 13).
 */
@RestController
@RequestMapping("/api/bridge-to-goal")
@RequiredArgsConstructor
public class BridgeToGoalController {

    private final BridgeToGoalService bridgeToGoalService;
    private final BridgeGoalTargetService bridgeGoalTargetService;

    @GetMapping
    public ResponseEntity<BridgeToGoalResponse> getBridgeToGoal(
            @RequestParam(required = false) String mois
    ) {
        return ResponseEntity.ok(bridgeToGoalService.simulerTrajectoire(mois));
    }

    @GetMapping("/target")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BridgeGoalTargetResponse> getMonthlyTarget(@RequestParam(required = false) String mois) {
        return ResponseEntity.ok(bridgeGoalTargetService.get(mois));
    }

    @PutMapping("/target")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BridgeGoalTargetResponse> saveMonthlyTarget(@RequestBody BridgeGoalTargetRequest request) {
        return ResponseEntity.ok(bridgeGoalTargetService.save(request));
    }
}
