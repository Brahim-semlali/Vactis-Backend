package com.vactis.service.medecin;

import com.vactis.dto.medecin.MedecinFilterOptionsResponse;
import com.vactis.dto.medecin.MedecinKpiResponse;
import com.vactis.dto.medecin.MedecinMetaResponse;
import com.vactis.dto.medecin.MedecinPageResponse;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.RisqueUrgence;
import com.vactis.model.medecin.SegmentMedecin;
import com.vactis.model.medecin.StatutMedecin;
import com.vactis.model.medecin.StatutPilotage;
import com.vactis.repository.medecin.MedecinRepository;
import com.vactis.service.action.ActionService;
import com.vactis.service.data.ExcelImportService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MedecinService {
    private final MedecinRepository medecinRepository;
    private final ActionService actionService;
    private final ExcelImportService excelImportService;

    public void syncMedecinsFromDataFictif() {
        excelImportService.importFictifExcelAndSyncMedecins();
    }

    //Recupere tous les medecins
    public List<Medecin> findAll(){
        return medecinRepository.findAll();
    }

    //Retrouve un medecin par son code
    public Medecin findByCodeMedecin(String codeMedecin) {
        if (codeMedecin == null) {
            return null;
        }

        String normalizedCode = codeMedecin.trim();
        if (normalizedCode.isEmpty()) {
            return null;
        }

        return medecinRepository.findByCodeMedecinIgnoreCase(normalizedCode).orElse(null);
    }

    //Retrouve un medecin par son id
    public Medecin findById(Long id){
        return medecinRepository.findById(id).orElse(null);
    }

    //Retrouve medecins par statu
    public List<Medecin> findByStatut(StatutMedecin statutMedecin){
        return medecinRepository.findByStatut(statutMedecin);
    }

    //le nombre des medecins
    public Long countAllMedecins(){
        return medecinRepository.countAllMedecins();
    }

    //Retrouve les medecins par segment
    public List<Medecin> findMedecinsBySegement(SegmentMedecin segmentMedecin){
        return medecinRepository.findBySegment(segmentMedecin);
    }

    //le nombre des medecins par statu pilotage
    public Long countAllByStatutPilotage(StatutPilotage statutPilotage){
        return medecinRepository.countAllByStatutPilotage(statutPilotage);
    }

    //Retrouve les medecins par statu pilotage
    public List<Medecin> findMedecinsByStatusPilotage(StatutPilotage statutPilotage){
        return medecinRepository.findAllByStatutPilotage(statutPilotage);
    }

    //Recherche les medecins avec filtres
    public List<Medecin> searchMedecins(
            String search,
            StatutPilotage statutPilotage,
            SegmentMedecin segment,
            String specialite,
            RisqueUrgence risqueUrgence,
            String organisme
    ){
        return medecinRepository.searchMedecins(
                normalize(search),
                statutPilotage,
                segment,
                normalize(specialite),
                risqueUrgence,
                normalize(organisme)
        );
    }

    //Recupere les options des filtres
    public MedecinFilterOptionsResponse getFilterOptions(){
        MedecinFilterOptionsResponse filters = new MedecinFilterOptionsResponse();
        filters.setSpecialites(medecinRepository.findDistinctSpecialites());
        filters.setOrganismes(medecinRepository.findDistinctOrganismes());
        return filters;
    }

    //Recupere les KPI de la page medecins
    public MedecinKpiResponse getKpis(){
        MedecinKpiResponse kpis = new MedecinKpiResponse();
        kpis.setTotal(medecinRepository.countAllMedecins());
        kpis.setSegmentsAB(medecinRepository.countBySegmentIn(List.of(SegmentMedecin.A, SegmentMedecin.B)));
        kpis.setSurveillance(medecinRepository.countAllByStatutPilotage(StatutPilotage.SURVEILLANCE));
        kpis.setOnboarding(medecinRepository.countAllByStatutPilotage(StatutPilotage.ONBOARDING));
        kpis.setSilenceCritique(medecinRepository.countAllByStatutPilotage(StatutPilotage.SILENCE_CRITIQUE));
        kpis.setActionsEnCours(actionService.countPlanifiees());
        return kpis;
    }

    //Recupere la page medecins complete
    public MedecinPageResponse getMedecinPage(
            String search,
            StatutPilotage statutPilotage,
            SegmentMedecin segment,
            String specialite,
            RisqueUrgence risqueUrgence,
            String organisme
    ){
        List<Medecin> items = searchMedecins(
                search,
                statutPilotage,
                segment,
                specialite,
                risqueUrgence,
                organisme
        );

        MedecinMetaResponse meta = new MedecinMetaResponse();
        meta.setAffiches((long) items.size());
        meta.setCharges(medecinRepository.countAllMedecins());

        MedecinPageResponse response = new MedecinPageResponse();
        response.setItems(items);
        response.setKpis(getKpis());
        response.setMeta(meta);
        response.setFilters(getFilterOptions());
        return response;
    }

    private String normalize(String value){
        if(value == null){
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
