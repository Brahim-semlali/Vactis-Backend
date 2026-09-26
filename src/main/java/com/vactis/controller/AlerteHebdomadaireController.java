package com.vactis.controller;

import com.vactis.model.action.Action;
import com.vactis.model.alerte.AlerteHebdomadaire;
import com.vactis.model.alerte.StatutAlerte;
import com.vactis.service.AlerteHebdomadaireService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST pour la surveillance précoce et les alertes hebdomadaires (Section 10).
 */
@RestController
@RequestMapping("/api/alertes")
@RequiredArgsConstructor
public class AlerteHebdomadaireController {

    private final AlerteHebdomadaireService alerteHebdomadaireService;

    @GetMapping
    public ResponseEntity<List<AlerteHebdomadaire>> getAlertesActives() {
        return ResponseEntity.ok(alerteHebdomadaireService.getAlertesActives());
    }

    @GetMapping("/toutes")
    public ResponseEntity<List<AlerteHebdomadaire>> getToutesAlertes() {
        return ResponseEntity.ok(alerteHebdomadaireService.getToutesAlertes());
    }

    @PostMapping("/batch-run")
    public ResponseEntity<Map<String, Object>> executerBatchManuellement() {
        List<AlerteHebdomadaire> alertes = alerteHebdomadaireService.executerBatchHebdomadaire();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Batch hebdomadaire exécuté avec succès.",
                "nbAlertesCreees", alertes.size(),
                "alertes", alertes
        ));
    }

    @PatchMapping("/{id}/statut")
    public ResponseEntity<AlerteHebdomadaire> modifierStatutAlerte(
            @PathVariable Long id,
            @RequestParam StatutAlerte statut
    ) {
        return ResponseEntity.ok(alerteHebdomadaireService.traiterAlerte(id, statut));
    }

    @PostMapping("/{id}/generer-action")
    public ResponseEntity<Action> genererActionDepuisAlerte(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String username = authentication != null ? authentication.getName() : "Commercial";
        return ResponseEntity.ok(alerteHebdomadaireService.genererActionDepuisAlerte(id, username));
    }
}
