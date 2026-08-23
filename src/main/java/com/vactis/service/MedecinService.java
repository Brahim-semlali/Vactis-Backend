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
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;

import com.vactis.service.Activite.SegmentationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Service métier pour la gestion du portefeuille médecins, la segmentation et le calcul des KPIs
@Service
@RequiredArgsConstructor
public class MedecinService {
    private final MedecinRepository medecinRepository;
    private final ActionService actionService;
    private final ExcelImportService excelImportService;
    private final ControleService controleService;
    private final SegmentationService segmentationService;
    private final ExtractionDonneesRepository extractionDonneesRepository;

    // Déclenche la synchronisation des médecins depuis le fichier Excel fictif
    public void syncMedecinsFromDataFictif() {
        excelImportService.importFictifExcelAndSyncMedecins();
    }

    // Retourne tous les médecins du portefeuille
    public List<Medecin> findAll(){
        return medecinRepository.findAll();
    }

    // Recherche un médecin par son code unique (ex: MED001)
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

    // Recherche un médecin par son identifiant technique
    public Medecin findById(Long id){
        return medecinRepository.findById(id).orElse(null);
    }

    // Met à jour la note de potentiel commercial saisie manuellement (1-5 ou null)
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

    // Retourne les médecins par statut de performance
    public List<Medecin> findByStatut(StatutMedecin statutMedecin){
        return medecinRepository.findByStatut(statutMedecin);
    }

    // Compte le nombre total de médecins en base
    public Long countAllMedecins(){
        return medecinRepository.countAllMedecins();
    }

    // Retourne les médecins d'un segment donné
    public List<Medecin> findMedecinsBySegement(String segment){
        return medecinRepository.findBySegment(segment);
    }

    // Compte les médecins par statut de pilotage commercial
    public Long countAllByStatutPilotage(StatutPilotage statutPilotage){
        return medecinRepository.countAllByStatutPilotage(statutPilotage);
    }

    // Retourne les médecins par statut de pilotage commercial
    public List<Medecin> findMedecinsByStatusPilotage(StatutPilotage statutPilotage){
        return medecinRepository.findAllByStatutPilotage(statutPilotage);
    }

    // Recherche les médecins selon des critères multiples (nom, segment, spécialité, etc.)
    public List<Medecin> searchMedecins(
            String search,
            StatutPilotage statutPilotage,
            String statut,
            String segment,
            String specialite,
            RisqueUrgence risqueUrgence,
            String organisme,
            Boolean sansNoteInput
    ){
        return medecinRepository.searchMedecins(
                normalize(search),
                statutPilotage,
                normalize(statut),
                normalize(segment),
                normalize(specialite),
                risqueUrgence,
                normalize(organisme),
                sansNoteInput
        );
    }

    // Retourne les options de filtres distinctes (spécialités, organismes, statuts, segments)
    public MedecinFilterOptionsResponse getFilterOptions(){
        MedecinFilterOptionsResponse filters = new MedecinFilterOptionsResponse();
        filters.setSpecialites(medecinRepository.findDistinctSpecialites());
        filters.setOrganismes(medecinRepository.findDistinctOrganismes());
        filters.setStatuts(controleService.getEtatsActifs(TypeControle.STATUT));
        filters.setSegments(controleService.getEtatsActifs(TypeControle.SEGEMENTS));
        return filters;
    }

    // Calcule les KPIs du portefeuille médecins (total, segments, pilotage, actions en cours)
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
        kpis.setSurveillance(medecinRepository.countByStatutIgnoreCase("SURVEILLANCE"));
        kpis.setOnboarding(medecinRepository.countByStatutIgnoreCase("ONBOARDING"));
        kpis.setSilenceCritique(medecinRepository.countByStatutIgnoreCase("SILENCE_CRITIQUE"));
        kpis.setActionsEnCours(actionService.countPlanifiees());
        kpis.setSansNoteInput(medecinRepository.countByNoteInputIsNull());
        return kpis;
    }

    // Recalcule dynamiquement les statuts (selon les règles Controle) et segments (A/B/C/D) de tous les médecins
    public void recalculerStatutsEtSegmentsDynamiques() {
        List<Medecin> medecins = medecinRepository.findAll();
        if (medecins.isEmpty()) return;

        List<LocalDate> dates = extractionDonneesRepository.findAllDatesDescending();
        YearMonth ymM = dates.isEmpty() ? YearMonth.now() : YearMonth.from(dates.get(0));
        YearMonth ymMm1 = ymM.minusMonths(1);

        Map<String, Long> caM = buildCaMapForMonth(ymM);
        Map<String, Long> caMm1 = buildCaMapForMonth(ymMm1);

        boolean modifie = false;

        List<Object[]> totalCasRows = extractionDonneesRepository.countCasGroupedByMedecin();
        Map<Long, Long> totalCasMap = new HashMap<>();
        for (Object[] row : totalCasRows) {
            if (row[0] != null && row[1] != null) {
                totalCasMap.put((Long) row[0], ((Number) row[1]).longValue());
            }
        }

        for (Medecin m : medecins) {
            String key = String.valueOf(m.getId());
            long valM = caM.getOrDefault(key, 0L);
            long valMm1 = caMm1.getOrDefault(key, 0L);

            m.setCaMois((int) valM);
            m.setCaBaseline((int) valMm1);
            m.setTotalCas(totalCasMap.getOrDefault(m.getId(), 0L).intValue());

            String statutDynamique = null;

            if (valM == 0 && valMm1 == 0) {
                statutDynamique = m.getStatutPilotage() != null ? m.getStatutPilotage().name() : "EXCLU";
            } else if (valMm1 == 0 && valM > 0) {
                statutDynamique = "ONBOARDING";
            } else if (valMm1 > 0) {
                double variation = ((double) (valM - valMm1) / (double) valMm1) * 100.0;
                long varRounded = Math.round(variation);

                statutDynamique = controleService.determinerEtat(TypeControle.STATUT, varRounded);
                if (statutDynamique == null) {
                    if (varRounded > 20) statutDynamique = "PROGRESSION";
                    else if (varRounded >= -10) statutDynamique = "ACTIF_STABLE";
                    else if (varRounded >= -40) statutDynamique = "SURVEILLANCE";
                    else if (varRounded >= -70) statutDynamique = "RETENTION";
                    else statutDynamique = "SILENCE_CRITIQUE";
                }
            } else {
                statutDynamique = "ACTIF_STABLE";
            }

            m.setCaMois((int) valM);
            m.setCaBaseline((int) valMm1);

            if (statutDynamique != null && !statutDynamique.equalsIgnoreCase(m.getStatut())) {
                m.setStatut(statutDynamique.toUpperCase());
                try {
                    m.setStatutPilotage(StatutPilotage.valueOf(statutDynamique.toUpperCase()));
                } catch (Exception ignored) {}
            }
            modifie = true;
        }

        if (modifie) {
            medecinRepository.saveAll(medecins);
        }

        // Recalcul du score de valeur et des segments A/B/C/D selon la formule Anapath
        segmentationService.recalculerSegmentationPortefeuille();
    }

    private Map<String, Long> buildCaMapForMonth(YearMonth ym) {
        List<Object[]> rows = extractionDonneesRepository.sumCaByMedecinAndDateRange(ym.atDay(1), ym.atEndOfMonth());
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] != null && row[1] != null) {
                map.put(String.valueOf((Long) row[0]), ((Number) row[1]).longValue());
            }
        }
        return map;
    }

    // Construit la réponse complète de la page médecins (liste filtrée, KPIs, méta, filtres)
    public MedecinPageResponse getMedecinPage(
            String search,
            StatutPilotage statutPilotage,
            String statut,
            String segment,
            String specialite,
            RisqueUrgence risqueUrgence,
            String organisme,
            Boolean sansNoteInput
    ){
        recalculerStatutsEtSegmentsDynamiques();

        List<Medecin> items = searchMedecins(
                search,
                statutPilotage,
                statut,
                segment,
                specialite,
                risqueUrgence,
                organisme,
                sansNoteInput
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

    // Nettoie et normalise une chaîne (trim + null si vide)
    private String normalize(String value){
        if(value == null){
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
