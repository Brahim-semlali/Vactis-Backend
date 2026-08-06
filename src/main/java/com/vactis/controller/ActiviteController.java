package com.vactis.controller;

import com.vactis.dto.activite.ComparaisonResponse;
import com.vactis.dto.activite.KpiMensuelResponse;
import com.vactis.service.ActiviteService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Contrôleur REST pour le suivi de l'activité mensuelle (KPIs, comparaisons, mois disponibles)
@RestController
@RequestMapping("/api/activite")
@RequiredArgsConstructor
public class ActiviteController {

    private final ActiviteService activiteService;

    // Retourne les KPIs d'activité pour un mois donné (CA, cas, médecins actifs, etc.)
    @GetMapping("/kpis-mensuels")
    public ResponseEntity<KpiMensuelResponse> getKpisMensuels(
            @RequestParam(name = "mois", required = false) String mois
    ) {
        return ResponseEntity.ok(activiteService.getKpisMensuels(mois));
    }

    // Compare les métriques du mois sélectionné avec M-1 et une fenêtre de référence glissante
    @GetMapping("/comparaison")
    public ResponseEntity<ComparaisonResponse> getComparaison(
            @RequestParam(name = "mois", required = false) String mois,
            @RequestParam(name = "metrique", required = false) String metrique,
            @RequestParam(name = "fenetreRef", required = false) Integer fenetreRef
    ) {
        return ResponseEntity.ok(activiteService.getComparaison(mois, metrique, fenetreRef));
    }

    // Retourne la liste des mois disponibles en base, du plus récent au plus ancien
    @GetMapping("/mois-disponibles")
    public ResponseEntity<List<String>> getMoisDisponibles() {
        return ResponseEntity.ok(activiteService.getMoisDisponibles());
    }
}
