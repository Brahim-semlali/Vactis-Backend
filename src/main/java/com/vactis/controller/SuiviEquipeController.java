package com.vactis.controller;

import com.vactis.dto.suiviequipe.SuiviEquipeDtos.CommercialResume;
import com.vactis.dto.suiviequipe.SuiviEquipeDtos.EvolutionMedecin;
import com.vactis.dto.suiviequipe.SuiviEquipeDtos.ReclamationLigne;
import com.vactis.dto.suiviequipe.SuiviEquipeDtos.VisiteLigne;
import com.vactis.service.SuiviEquipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

// Suivi équipe : historique par commercial et évolution observée des médecins après visite
@RestController
@RequestMapping("/api/suivi-equipe")
@RequiredArgsConstructor
public class SuiviEquipeController {

    private final SuiviEquipeService suiviEquipeService;

    @GetMapping("/commerciaux")
    public List<CommercialResume> getCommerciaux(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return suiviEquipeService.getCommerciaux(debut, fin);
    }

    @GetMapping("/visites")
    public List<VisiteLigne> getVisites(
            @RequestParam(required = false) String commercial,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String statut) {
        return suiviEquipeService.getVisites(commercial, debut, fin, type, statut);
    }

    @GetMapping("/reclamations")
    public List<ReclamationLigne> getReclamations(
            @RequestParam(required = false) String commercial,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return suiviEquipeService.getReclamations(commercial, debut, fin);
    }

    @GetMapping("/medecins/{medecinId}/evolution")
    public EvolutionMedecin getEvolutionMedecin(@PathVariable Long medecinId) {
        return suiviEquipeService.getEvolutionMedecin(medecinId);
    }
}
