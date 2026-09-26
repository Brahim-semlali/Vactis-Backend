package com.vactis.service;

import com.vactis.dto.medecin.MedecinFilterOptionsResponse;
import com.vactis.dto.medecin.MedecinGeolocaliseDto;
import com.vactis.dto.medecin.MedecinKpiResponse;
import com.vactis.dto.medecin.MedecinMetaResponse;
import com.vactis.dto.medecin.MedecinPageResponse;
import com.vactis.dto.medecin.MedecinSansLocalisationDto;
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
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    // Retourne l'évolution mensuelle du CA et du nombre de cas d'un médecin
    public List<Map<String, Object>> getEvolutionByMedecin(Long id) {
        if (id == null || medecinRepository.findById(id).isEmpty()) {
            return List.of();
        }

        List<LocalDate> dates = extractionDonneesRepository.findDatesReceptionByMedecinId(id);
        if (dates.isEmpty()) {
            return List.of();
        }

        List<YearMonth> months = dates.stream()
                .map(YearMonth::from)
                .distinct()
                .sorted()
                .toList();

        List<Map<String, Object>> evolution = new ArrayList<>();
        for (YearMonth month : months) {
            Map<String, Long> caMap = buildCaMapForMonth(month);
            Map<String, Long> casMap = buildCasMapForMonth(month);

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("month", month.format(DateTimeFormatter.ofPattern("yyyy-MM")));
            point.put("label", month.getMonth().getDisplayName(TextStyle.SHORT, Locale.FRANCE) + " " + month.getYear());
            point.put("ca", caMap.getOrDefault(String.valueOf(id), 0L));
            point.put("cas", casMap.getOrDefault(String.valueOf(id), 0L));
            evolution.add(point);
        }

        return evolution;
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
        medecinRepository.save(medecin);
        segmentationService.recalculerSegmentationPortefeuille();
        return medecinRepository.findById(id).orElse(medecin);
    }

    // Retourne uniquement les médecins géolocalisés pour la carte Zone intelligence
    public List<MedecinGeolocaliseDto> getMedecinsGeolocalises() {
        return medecinRepository.findByLatitudeIsNotNullAndLongitudeIsNotNull()
                .stream()
                .map(m -> new MedecinGeolocaliseDto(
                        m.getId(),
                        m.getCodeMedecin(),
                        m.getNom(),
                        m.getPrenom(),
                        m.getSpecialite(),
                        m.getOrganisme(),
                        m.getSegment(),
                        m.getStatut(),
                        m.getCaMois(),
                        m.getLatitude(),
                        m.getLongitude()
                ))
                .toList();
    }

    // Retourne les médecins sans coordonnées complètes pour la complétion admin
    public List<MedecinSansLocalisationDto> getMedecinsSansLocalisation() {
        return medecinRepository.findByLatitudeIsNullOrLongitudeIsNull()
                .stream()
                .map(m -> new MedecinSansLocalisationDto(
                        m.getId(),
                        m.getCodeMedecin(),
                        m.getNom(),
                        m.getPrenom(),
                        m.getSpecialite(),
                        m.getOrganisme(),
                        m.getVille(),
                        m.getSegment(),
                        m.getStatut()
                ))
                .toList();
    }

    // Met à jour les coordonnées géographiques d'un médecin existant
    public MedecinGeolocaliseDto updateLocalisation(Long id, Double latitude, Double longitude) {
        Medecin medecin = medecinRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Médecin introuvable : " + id));

        if (latitude != null && (latitude < -90.0 || latitude > 90.0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La latitude doit être comprise entre -90.0 et 90.0");
        }
        if (longitude != null && (longitude < -180.0 || longitude > 180.0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La longitude doit être comprise entre -180.0 et 180.0");
        }

        medecin.setLatitude(latitude);
        medecin.setLongitude(longitude);
        Medecin saved = medecinRepository.save(medecin);

        return new MedecinGeolocaliseDto(
                saved.getId(),
                saved.getCodeMedecin(),
                saved.getNom(),
                saved.getPrenom(),
                saved.getSpecialite(),
                saved.getOrganisme(),
                saved.getSegment(),
                saved.getStatut(),
                saved.getCaMois(),
                saved.getLatitude(),
                saved.getLongitude()
        );
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

    public static StatutPilotage normalizeStatutPilotage(String statut) {
        if (statut == null) {
            return StatutPilotage.ACTIF_STABLE;
        }

        String normalized = statut.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SILENCE_CRITIQUE" -> StatutPilotage.RETENTION;
            case "EXCLU" -> StatutPilotage.INACTIF;
            case "ACTIF_STABLE", "PROGRESSION", "SURVEILLANCE", "RETENTION", "ONBOARDING", "A_REACTIVER", "INACTIF", "ACTIF" -> StatutPilotage.valueOf(normalized);
            default -> StatutPilotage.ACTIF_STABLE;
        };
    }

    // Recalcule dynamiquement les statuts (selon les règles Controle) et segments (A/B/C/D) de tous les médecins
    public void recalculerStatutsEtSegmentsDynamiques() {
        List<Medecin> medecins = medecinRepository.findAll();
        if (medecins.isEmpty()) return;

        YearMonth ymM = YearMonth.now();
        YearMonth ymMm1 = ymM.minusMonths(1);

        Map<String, Long> caM = buildCaMapForMonth(ymM);
        Map<String, Long> caMm1 = buildCaMapForMonth(ymMm1);
        Map<String, Long> caMm2 = buildCaMapForMonth(ymM.minusMonths(2));
        Map<String, Long> caMm3 = buildCaMapForMonth(ymM.minusMonths(3));
        Map<String, Long> casM = buildCasMapForMonth(ymM);
        Map<String, Long> casMm1 = buildCasMapForMonth(ymMm1);
        Map<String, Long> casMm2 = buildCasMapForMonth(ymM.minusMonths(2));
        Map<String, Long> casMm3 = buildCasMapForMonth(ymM.minusMonths(3));

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

            String statutDynamique;
            LocalDate limite60Jours = LocalDate.now().minusDays(60);
            boolean actif60Jours = m.getDateDerniereActivite() != null && !m.getDateDerniereActivite().isBefore(limite60Jours);

            if (Boolean.TRUE.equals(m.getIsAReactiverManuel()) && valM == 0) {
                statutDynamique = "A_REACTIVER";
            } else if (valM == 0) {
                statutDynamique = actif60Jours ? "RETENTION" : "INACTIF";
            } else if (valMm1 == 0 && valM > 0) {
                statutDynamique = "ONBOARDING";
            } else {
                double caReference = average(caMm1, caMm2, caMm3, key);
                double casReference = average(casMm1, casMm2, casMm3, key);
                double variationCa = ((valM - caReference) / Math.max(caReference, 300.0)) * 100.0;
                double variationCas = ((casM.getOrDefault(key, 0L) - casReference)
                        / Math.max(casReference, 1.0)) * 100.0;
                double variationMixte = (0.60 * variationCa) + (0.40 * variationCas);

                if (variationMixte > 20.0) {
                    statutDynamique = "PROGRESSION";
                } else if (variationMixte >= -10.0) {
                    statutDynamique = "ACTIF_STABLE";
                } else if (variationMixte >= -40.0) {
                    statutDynamique = "SURVEILLANCE";
                } else {
                    statutDynamique = "RETENTION";
                }

                // Section 4 — Activité Irrégulière : neutralisation des baisses pour prescripteurs à profil atypique régulier
                if (("SURVEILLANCE".equals(statutDynamique) || "RETENTION".equals(statutDynamique))
                        && isPrescripteurIrregulier(m, valM, caMm1.getOrDefault(key, 0L), caMm2.getOrDefault(key, 0L), caMm3.getOrDefault(key, 0L), casM.getOrDefault(key, 0L), casReference)) {
                    m.setIsProfilIrregulier(true);
                    statutDynamique = "ACTIF_STABLE";
                } else {
                    m.setIsProfilIrregulier(false);
                }
            }

            m.setCaMois((int) valM);
            m.setCaBaseline((int) valMm1);

            if (!statutDynamique.equalsIgnoreCase(m.getStatut())) {
                m.setStatut(statutDynamique.toUpperCase());
            }

            StatutPilotage normalizedPilotage = normalizeStatutPilotage(statutDynamique);
            if (m.getStatutPilotage() != normalizedPilotage) {
                m.setStatutPilotage(normalizedPilotage);
            }
            modifie = true;
        }

        if (modifie) {
            medecinRepository.saveAll(medecins);
        }

        // Recalcul du score de valeur et des segments A/B/C/D selon la formule Anapath
        segmentationService.recalculerSegmentationPortefeuille();
    }

    private boolean isPrescripteurIrregulier(
            Medecin m,
            long caCurr,
            long caMm1,
            long caMm2,
            long caMm3,
            long casCurr,
            double casRef
    ) {
        if (m == null) return false;

        // Condition 1 : Ancienneté >= 6 mois
        boolean ancienneteOk = m.getDatePremiereCollaboration() != null
                && !m.getDatePremiereCollaboration().plusMonths(6).isAfter(LocalDate.now());
        if (!ancienneteOk && m.getCreatedAt() != null) {
            ancienneteOk = !m.getCreatedAt().toLocalDate().plusMonths(6).isAfter(LocalDate.now());
        }
        if (!ancienneteOk) return false;

        // Condition 2 : Au moins 2 mois actifs parmi M-1, M-2, M-3
        int moisActifs = (caMm1 > 0 ? 1 : 0) + (caMm2 > 0 ? 1 : 0) + (caMm3 > 0 ? 1 : 0);
        if (moisActifs < 2) return false;

        // Condition 3 : Variabilité historique >= 30%
        long maxHist = Math.max(caMm1, Math.max(caMm2, caMm3));
        long minHist = Math.min(caMm1, Math.min(caMm2, caMm3));
        if (maxHist > 0 && ((double)(maxHist - minHist) / maxHist) < 0.30) return false;

        // Condition 4 : Volume total de cas >= 5
        if (m.getTotalCas() != null && m.getTotalCas() < 5) return false;

        // Condition 5 : Activité non nulle sur le mois courant
        if (caCurr <= 0) return false;

        // Condition 6 : Panier moyen préservé (>= 65% de la référence)
        if (casCurr > 0 && casRef > 0) {
            double panierCurr = (double) caCurr / casCurr;
            double caRefMoy = (caMm1 + caMm2 + caMm3) / 3.0;
            double panierRef = caRefMoy / casRef;
            if (panierRef > 0 && (panierCurr / panierRef) < 0.65) return false;
        }

        // Condition 7 : Absence de réclamation bloquante
        if (m.getCommentaire() != null && m.getCommentaire().toLowerCase().contains("reclamation")) return false;

        // Condition 8 : Statut non inactif
        return m.getStatut() == null || !"INACTIF".equalsIgnoreCase(m.getStatut());
    }

    // Active ou désactive manuellement le statut "À réactiver" pour un médecin (Section 1)
    public Medecin toggleAReactiverManuel(Long id, Boolean active) {
        Medecin m = medecinRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Médecin introuvable"));
        boolean nouvelEtat = active != null ? active : !Boolean.TRUE.equals(m.getIsAReactiverManuel());
        m.setIsAReactiverManuel(nouvelEtat);
        if (nouvelEtat && (m.getCaMois() == null || m.getCaMois() == 0)) {
            m.setStatut("A_REACTIVER");
            m.setStatutPilotage(StatutPilotage.A_REACTIVER);
        } else if (!nouvelEtat && "A_REACTIVER".equalsIgnoreCase(m.getStatut())) {
            m.setStatut("INACTIF");
            m.setStatutPilotage(StatutPilotage.INACTIF);
        }
        return medecinRepository.save(m);
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

    private Map<String, Long> buildCasMapForMonth(YearMonth ym) {
        List<Object[]> rows = extractionDonneesRepository.countCasByMedecinAndDateRange(ym.atDay(1), ym.atEndOfMonth());
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] != null && row[1] != null) {
                map.put(String.valueOf((Long) row[0]), ((Number) row[1]).longValue());
            }
        }
        return map;
    }

    private double average(Map<String, Long> first, Map<String, Long> second, Map<String, Long> third, String key) {
        return (first.getOrDefault(key, 0L) + second.getOrDefault(key, 0L) + third.getOrDefault(key, 0L)) / 3.0;
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
