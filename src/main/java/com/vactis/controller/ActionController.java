package com.vactis.controller;

import com.vactis.dto.action.ActionPageResponse;
import com.vactis.model.action.Action;
import com.vactis.model.action.EtatAction;
import com.vactis.model.action.UrgenceAction;
import com.vactis.model.medecin.StatutPilotage;
import com.vactis.service.ActionService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vactis.dto.action.SaisieRetourTerrainRequest;
import com.vactis.dto.action.SaisieVisiteLibreRequest;
import com.vactis.dto.action.VisiteLibreResponse;
import com.vactis.dto.medecin.FicheContextuelleResponse;
import com.vactis.model.medecin.RetourTerrain;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

// Contrôleur REST exposant les endpoints de gestion des actions de pilotage commercial
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/actions")
public class ActionController {
    private final ActionService actionService;

    // Récupère la liste des actions filtrées avec KPIs, métadonnées et options de filtres
    @GetMapping
    public ActionPageResponse getActions(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) String segment,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UrgenceAction urgence,
            @RequestParam(required = false) EtatAction etatAction,
            @RequestParam(required = false) Boolean backlog,
            @RequestParam(required = false) String commercial,
            @RequestParam(required = false) String lieuOrganisme
    ) {
        return actionService.getActionPage(
                search,
                statut,
                segment,
                action,
                urgence,
                etatAction,
                backlog,
                commercial,
                lieuOrganisme
        );
    }

    // Récupère une action par son identifiant unique
    @GetMapping("/{id}")
    public Action getActionById(@PathVariable Long id) {
        return actionService.findById(id);
    }

    // Permet à un commercial de réserver une action VACTIS
    @PostMapping("/{id}/reserver")
    public Action reserverAction(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails != null ? userDetails.getUsername() : "Commercial";
        return actionService.reserverAction(id, username);
    }

    // Valide et enregistre le retour terrain direct d'une action VACTIS
    @PostMapping("/{id}/retour-terrain")
    public Action soumettreRetourTerrain(
            @PathVariable Long id,
            @RequestBody SaisieRetourTerrainRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String username = userDetails != null ? userDetails.getUsername() : "Commercial";
        return actionService.soumettreRetourTerrain(id, request, username);
    }

    // Enregistre une visite commerciale libre hors VACTIS
    @PostMapping("/visite-libre")
    public RetourTerrain creerVisiteLibre(
            @RequestBody SaisieVisiteLibreRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String username = userDetails != null ? userDetails.getUsername() : "Commercial";
        return actionService.creerVisiteLibre(request, username);
    }

    // Récupère la liste de toutes les visites commerciales libres (hors VACTIS)
    @GetMapping("/visite-libre")
    public List<VisiteLibreResponse> getVisitesLibres() {
        return actionService.getVisitesLibres();
    }

    // Récupère les données de la fiche contextuelle d'un médecin
    @GetMapping("/medecins/{medecinId}/fiche-contextuelle")
    public FicheContextuelleResponse getFicheContextuelle(@PathVariable Long medecinId) {
        return actionService.getFicheContextuelle(medecinId);
    }
}
