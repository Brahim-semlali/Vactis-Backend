package com.vactis.controller;

import com.vactis.dto.activite.ComparaisonResponse;
import com.vactis.dto.activite.FluxAgregesResponse;
import com.vactis.dto.activite.KpiMensuelResponse;
import com.vactis.dto.activite.StatutRepartitionResponse;
import com.vactis.dto.activite.TopMouvementsResponse;
import com.vactis.dto.activite.TransitionsStatutsResponse;
import com.vactis.dto.activite.ActionsVactisResponse;
import com.vactis.dto.activite.CompteRenduTerrainResponse;
import com.vactis.service.ActivitePortefeuilleService;
import com.vactis.service.ActiviteService;
import com.vactis.service.ActiviteTerrainService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Contrôleur REST pour le suivi de l'activité mensuelle (Niveau 1 : KPIs, comparaisons ; Niveau 2 : statuts, flux, top mouvements ; Niveau 3 : exécution terrain)
@RestController
@RequestMapping("/api/activite")
@RequiredArgsConstructor
public class ActiviteController {

    private final ActiviteService activiteService;
    private final ActivitePortefeuilleService activitePortefeuilleService;
    private final ActiviteTerrainService activiteTerrainService;

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

    // --- Niveau 2 — Dynamique du portefeuille médecins ---

    // Répartition des 8 statuts VACTIS pour le mois M
    @GetMapping("/statuts")
    public ResponseEntity<StatutRepartitionResponse> getStatuts(
            @RequestParam(name = "mois", required = false) String mois
    ) {
        return ResponseEntity.ok(activitePortefeuilleService.getRepartitionStatuts(mois));
    }

    // Transitions de statuts entre M-1 et M avec 5 compteurs agrégés
    @GetMapping("/statuts/transitions")
    public ResponseEntity<TransitionsStatutsResponse> getTransitionsStatuts(
            @RequestParam(name = "mois", required = false) String mois
    ) {
        return ResponseEntity.ok(activitePortefeuilleService.getTransitionsStatuts(mois));
    }

    // Liste de toutes les paires (statut M-1 → statut M) triées par effectif décroissant
    @GetMapping("/statuts/flux")
    public ResponseEntity<FluxAgregesResponse> getFluxAgreges(
            @RequestParam(name = "mois", required = false) String mois
    ) {
        return ResponseEntity.ok(activitePortefeuilleService.getFluxAgreges(mois));
    }

    // Top progressions et top baisses par médecin pour la métrique demandée (ca ou cas)
    @GetMapping("/top-mouvements")
    public ResponseEntity<TopMouvementsResponse> getTopMouvements(
            @RequestParam(name = "mois", required = false) String mois,
            @RequestParam(name = "metrique", defaultValue = "ca") String metrique,
            @RequestParam(name = "limite", defaultValue = "10") int limite
    ) {
        return ResponseEntity.ok(activitePortefeuilleService.getTopMouvements(mois, metrique, limite));
    }

    // --- Niveau 3 — Exécution terrain ---

    // Lecture réalisation commerciale / actions VACTIS
    @GetMapping("/terrain/actions")
    public ResponseEntity<ActionsVactisResponse> getActionsVactis(
            @RequestParam(name = "mois", required = false) String mois
    ) {
        return ResponseEntity.ok(activiteTerrainService.getActionsVactis(mois));
    }

    // Compte-rendu terrain du mois
    @GetMapping("/terrain/compte-rendu")
    public ResponseEntity<CompteRenduTerrainResponse> getCompteRenduTerrain(
            @RequestParam(name = "mois", required = false) String mois
    ) {
        return ResponseEntity.ok(activiteTerrainService.getCompteRenduTerrain(mois));
    }
}
