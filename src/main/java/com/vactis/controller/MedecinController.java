package com.vactis.controller;

import com.vactis.dto.MedecinPageResponse;
import com.vactis.model.Medecin;
import com.vactis.model.RisqueUrgence;
import com.vactis.model.SegmentMedecin;
import com.vactis.model.StatutPilotage;
import com.vactis.service.MedecinService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
            @RequestParam(required = false) SegmentMedecin segment,
            @RequestParam(required = false) String specialite,
            @RequestParam(required = false) RisqueUrgence risqueUrgence,
            @RequestParam(required = false) String organisme
    ){
        return medecinService.getMedecinPage(
                search,
                statutPilotage,
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
}
