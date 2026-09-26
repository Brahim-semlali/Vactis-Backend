package com.vactis.controller;

import com.vactis.dto.recommandation.RecommandationsGlobalResponse;
import com.vactis.model.action.Action;
import com.vactis.service.RecommandationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Contrôleur REST pour les recommandations commerciales proactives (Section 11).
 */
@RestController
@RequestMapping("/api/recommandations")
@RequiredArgsConstructor
public class RecommandationController {

    private final RecommandationService recommandationService;

    @GetMapping
    public ResponseEntity<RecommandationsGlobalResponse> getRecommandations() {
        return ResponseEntity.ok(recommandationService.getRecommandations());
    }

    @PostMapping("/{medecinId}/creer-action")
    public ResponseEntity<Action> creerAction(
            @PathVariable Long medecinId,
            @RequestParam(defaultValue = "DEVELOPPEMENT") String type,
            Authentication authentication
    ) {
        String username = authentication != null ? authentication.getName() : "Commercial";
        return ResponseEntity.ok(recommandationService.creerActionDepuisRecommandation(medecinId, type, username));
    }
}
