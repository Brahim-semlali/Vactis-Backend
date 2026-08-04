package com.vactis.controller;

import com.vactis.dto.medecin.MedecinPageResponse;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.RisqueUrgence;
import com.vactis.model.medecin.StatutPilotage;
import com.vactis.service.MedecinService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/medecins")
public class MedecinController {
    private final MedecinService medecinService;

    //Recupere la page medecins avec filtresR
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

    //Retrouver un medcin par son code
    @GetMapping("/code/{code}")
    public Medecin getMedecinByCode(@PathVariable String code){
        return medecinService.findByCodeMedecin(code);
    }

    //Retrouve un medecin par son id
    @GetMapping("/{id}")
    public Medecin getMedecinById(@PathVariable Long id){
        return medecinService.findById(id);
    }

    //Synchronise / extrait les medecins depuis la table data_fictif
    @org.springframework.web.bind.annotation.PostMapping("/sync")
    public void syncFromDataFictif(){
        medecinService.syncMedecinsFromDataFictif();
    }

    /**
     * Met à jour uniquement le champ noteInput du médecin (Potentiel commercial).
     * Body : { "noteInput": 4.0 }  ou  { "noteInput": null } pour effacer.
     * Retourne 400 si la valeur est hors de l'intervalle [1, 5].
     */
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
}
