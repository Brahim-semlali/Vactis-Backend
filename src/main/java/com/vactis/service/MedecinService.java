package com.vactis.service;

import com.vactis.dto.medecin.MedecinFilterOptionsResponse;
import com.vactis.dto.medecin.MedecinKpiResponse;
import com.vactis.dto.medecin.MedecinMetaResponse;
import com.vactis.dto.medecin.MedecinPageResponse;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.RisqueUrgence;
import com.vactis.model.medecin.StatutMedecin;
import com.vactis.model.medecin.StatutPilotage;
import com.vactis.model.Controle.TypeControle;
import com.vactis.repository.MedecinRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MedecinService {
    private final MedecinRepository medecinRepository;
    private final ActionService actionService;
    private final ExcelImportService excelImportService;
    private final ControleService controleService;
    private final SegmentationService segmentationService;

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

    //Met à jour uniquement le champ noteInput (1-5 ou null) — sans recalcul de segment
    public Medecin updateNoteInput(Long id, Double noteInput) {
        Medecin medecin = medecinRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Médecin introuvable"));

        if (noteInput != null && (noteInput < 1.0 || noteInput > 5.0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La note doit être comprise entre 1 et 5 (ou null pour effacer).");
        }

        medecin.setNoteInput(noteInput);
        return medecinRepository.save(medecin);
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
    public List<Medecin> findMedecinsBySegement(String segment){
        return medecinRepository.findBySegment(segment);
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
            String statut,
            String segment,
            String specialite,
            RisqueUrgence risqueUrgence,
            String organisme
    ){
        return medecinRepository.searchMedecins(
                normalize(search),
                statutPilotage,
                normalize(statut),
                normalize(segment),
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
        filters.setStatuts(controleService.getEtatsActifs(TypeControle.STATUT));
        filters.setSegments(controleService.getEtatsActifs(TypeControle.SEGEMENTS));
        return filters;
    }

    //Recupere les KPI de la page medecins
    public MedecinKpiResponse getKpis(){
        MedecinKpiResponse kpis = new MedecinKpiResponse();
        kpis.setTotal(medecinRepository.countAllMedecins());
        List<String> prioritySegments = controleService.getEtatsActifs(TypeControle.SEGEMENTS);
        if (prioritySegments.size() >= 2) {
            prioritySegments = prioritySegments.subList(0, prioritySegments.size() - 1);
        }
        kpis.setSegmentsAB(
                prioritySegments.isEmpty()
                        ? 0L
                        : medecinRepository.countBySegmentIn(prioritySegments)
        );
        kpis.setSurveillance(medecinRepository.countAllByStatutPilotage(StatutPilotage.SURVEILLANCE));
        kpis.setOnboarding(medecinRepository.countAllByStatutPilotage(StatutPilotage.ONBOARDING));
        kpis.setSilenceCritique(medecinRepository.countAllByStatutPilotage(StatutPilotage.SILENCE_CRITIQUE));
        kpis.setActionsEnCours(actionService.countPlanifiees());
        return kpis;
    }

    public void recalculerStatutsEtSegmentsDynamiques() {
        List<Medecin> medecins = medecinRepository.findAll();
        boolean modifie = false;

        for (Medecin m : medecins) {
            Long ca = m.getCaMois() != null ? m.getCaMois().longValue() : 0L;

            String statutDynamique = controleService.determinerEtat(TypeControle.STATUT, ca);
            if (statutDynamique != null && !statutDynamique.equals(m.getStatut())) {
                m.setStatut(statutDynamique);
                modifie = true;
            }
        }

        if (modifie) {
            medecinRepository.saveAll(medecins);
        }

        // Recalcul du score de valeur et des segments A/B/C/D selon la formule Anapath
        segmentationService.recalculerSegmentationPortefeuille();
    }

    //Recupere la page medecins complete
    public MedecinPageResponse getMedecinPage(
            String search,
            StatutPilotage statutPilotage,
            String statut,
            String segment,
            String specialite,
            RisqueUrgence risqueUrgence,
            String organisme
    ){
        recalculerStatutsEtSegmentsDynamiques();

        List<Medecin> items = searchMedecins(
                search,
                statutPilotage,
                statut,
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
