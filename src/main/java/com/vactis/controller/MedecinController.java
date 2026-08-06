package com.vactis.controller;

import com.vactis.dto.medecin.MedecinPageResponse;
import com.vactis.dto.medecin.RetourTerrainRequest;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.RetourTerrain;
import com.vactis.model.medecin.RisqueUrgence;
import com.vactis.model.medecin.StatutPilotage;
import com.vactis.service.MedecinService;
import com.vactis.service.RetourTerrainService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Contrôleur REST pour la gestion du portefeuille médecins et des retours terrain
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/medecins")
public class MedecinController {
    private final MedecinService medecinService;
    private final RetourTerrainService retourTerrainService;

    // Récupère la page des médecins avec filtres, KPIs et métadonnées
    @GetMapping
    public MedecinPageResponse getMedecins(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) StatutPilotage statutPilotage,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) String segment,
            @RequestParam(required = false) String specialite,
            @RequestParam(required = false) RisqueUrgence risqueUrgence,
            @RequestParam(required = false) String organisme
    ){
        return medecinService.getMedecinPage(
                search,
                statutPilotage,
                statut,
                segment,
                specialite,
                risqueUrgence,
                organisme
        );
    }

    // Recherche un médecin par son code unique (ex: MED001)
    @GetMapping("/code/{code}")
    public Medecin getMedecinByCode(@PathVariable String code){
        return medecinService.findByCodeMedecin(code);
    }

    // Récupère un médecin par son identifiant technique
    @GetMapping("/{id}")
    public Medecin getMedecinById(@PathVariable Long id){
        return medecinService.findById(id);
    }

    // Synchronise les médecins depuis le fichier Excel des données fictives
    @PostMapping("/sync")
    public void syncFromDataFictif(){
        medecinService.syncMedecinsFromDataFictif();
    }

    // Met à jour la note de potentiel commercial saisie manuellement (1-5 ou null)
    @PatchMapping("/{id}/note-input")
    public ResponseEntity<Medecin> updateNoteInput(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body
    ) {
        Double noteInput = null;
        if (body.containsKey("noteInput") && body.get("noteInput") != null) {
            noteInput = ((Number) body.get("noteInput")).doubleValue();
        }
        Medecin updated = medecinService.updateNoteInput(id, noteInput);
        return ResponseEntity.ok(updated);
    }

    // Ajoute un nouveau retour terrain (visite historisée) pour un médecin
    @PostMapping("/{id}/retours-terrain")
    public ResponseEntity<RetourTerrain> addRetourTerrain(
            @PathVariable Long id,
            @Valid @RequestBody RetourTerrainRequest request
    ) {
        RetourTerrain retour = retourTerrainService.addRetourTerrain(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(retour);
    }

    // Récupère l'historique des visites terrain d'un médecin, du plus récent au plus ancien
    @GetMapping("/{id}/retours-terrain")
    public ResponseEntity<List<RetourTerrain>> getRetoursTerrain(@PathVariable Long id) {
        List<RetourTerrain> retours = retourTerrainService.getRetoursTerrainByMedecin(id);
        return ResponseEntity.ok(retours);
    }
}
