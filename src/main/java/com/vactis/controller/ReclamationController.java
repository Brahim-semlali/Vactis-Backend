package com.vactis.controller;

import com.vactis.dto.reclamation.ReclamationRequest;
import com.vactis.model.reclamation.CategorieReclamation;
import com.vactis.model.reclamation.PrioriteReclamation;
import com.vactis.model.reclamation.Reclamation;
import com.vactis.model.reclamation.StatutReclamation;
import com.vactis.service.ReclamationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST pour le module de Ticketing Réclamations (Section 7).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reclamations")
public class ReclamationController {

    private final ReclamationService reclamationService;

    @GetMapping
    public ResponseEntity<List<Reclamation>> searchReclamations(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) StatutReclamation statut,
            @RequestParam(required = false) CategorieReclamation categorie,
            @RequestParam(required = false) PrioriteReclamation priorite
    ) {
        return ResponseEntity.ok(reclamationService.searchReclamations(search, statut, categorie, priorite));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Reclamation> getById(@PathVariable Long id) {
        return ResponseEntity.ok(reclamationService.getById(id));
    }

    @GetMapping("/medecin/{medecinId}")
    public ResponseEntity<List<Reclamation>> getByMedecin(@PathVariable Long medecinId) {
        return ResponseEntity.ok(reclamationService.getReclamationsByMedecin(medecinId));
    }

    @PostMapping
    public ResponseEntity<Reclamation> creerReclamation(
            @Valid @RequestBody ReclamationRequest request,
            Authentication authentication
    ) {
        String username = authentication != null ? authentication.getName() : "Commercial";
        Reclamation created = reclamationService.creerReclamation(request, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}/statut")
    public ResponseEntity<Reclamation> updateStatut(
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        String statutValue = body.get("statut");
        if (statutValue == null || statutValue.isBlank()) {
            throw new IllegalArgumentException("Le statut de réclamation est obligatoire.");
        }
        StatutReclamation statut = StatutReclamation.valueOf(statutValue.trim().toUpperCase());
        String solution = body.get("solution");
        String responsable = body.get("responsable");
        return ResponseEntity.ok(reclamationService.mettreAJourStatut(id, statut, solution, responsable));
    }

    @GetMapping("/kpi/ouvertes")
    public ResponseEntity<Map<String, Long>> countOuvertes() {
        return ResponseEntity.ok(Map.of("ouvertes", reclamationService.countOuvertes()));
    }
}
