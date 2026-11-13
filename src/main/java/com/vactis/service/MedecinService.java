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
import com.vactis.service.Activite.ActivitePortefeuilleService;
import com.vactis.service.Activite.StatutMensuelPolicy;
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
    private final ActivitePortefeuilleService activitePortefeuilleService;
    private final ExtractionDonneesRepository extractionDonneesRepository;

    // Déclenche la synchronisation des médecins depuis le fichier Excel fictif
    public void syncMedecinsFromDataFictif() {
        excelImportService.importFictifExcelAndSyncMedecins();
    }

    // Retourne tous les médecins du portefeuille
    public List<Medecin> findAll(){
        return medecinsEligibles();
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
        return medecinsEligibles().stream()
            .filter(m -> m.getLatitude() != null && m.getLongitude() != null)
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
        return medecinsEligibles().stream()
            .filter(m -> m.getLatitude() == null || m.getLongitude() == null)
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
        return medecinRepository.findByStatut(statutMedecin).stream()
            .filter(m -> !medecinRepository.isPseudoMedecin(m)).toList();
    }

    // Compte le nombre total de médecins en base
    public Long countAllMedecins(){
        return (long) medecinsEligibles().size();
    }

    // Retourne les médecins d'un segment donné
    public List<Medecin> findMedecinsBySegement(String segment){
        return medecinRepository.findBySegment(segment).stream()
            .filter(m -> !medecinRepository.isPseudoMedecin(m)).toList();
    }

    // Compte les médecins par statut de pilotage commercial
    public Long countAllByStatutPilotage(StatutPilotage statutPilotage){
        return (long) findMedecinsByStatusPilotage(statutPilotage).size();
    }

    // Retourne les médecins par statut de pilotage commercial
    public List<Medecin> findMedecinsByStatusPilotage(StatutPilotage statutPilotage){
        return medecinRepository.findAllByStatutPilotage(statutPilotage).stream()
            .filter(m -> !medecinRepository.isPseudoMedecin(m)).toList();
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
            ).stream().filter(m -> !medecinRepository.isPseudoMedecin(m)).toList();
    }

    // Retourne les options de filtres distinctes (spécialités, organismes, statuts, segments)
    public MedecinFilterOptionsResponse getFilterOptions(){
        MedecinFilterOptionsResponse filters = new MedecinFilterOptionsResponse();
        List<Medecin> medecins = medecinsEligibles();
        filters.setSpecialites(medecins.stream().map(Medecin::getSpecialite).filter(s -> s != null && !s.isBlank()).distinct().sorted().toList());
        filters.setOrganismes(medecins.stream().map(Medecin::getOrganisme).filter(o -> o != null && !o.isBlank()).distinct().sorted().toList());
        filters.setStatuts(controleService.getEtatsActifs(TypeControle.STATUT));
        filters.setSegments(controleService.getEtatsActifs(TypeControle.SEGEMENTS));
        return filters;
    }

    // Calcule les KPIs du portefeuille médecins (total, segments, pilotage, actions en cours)
    public MedecinKpiResponse getKpis(){
        MedecinKpiResponse kpis = new MedecinKpiResponse();
        List<Medecin> medecins = medecinsEligibles();
        kpis.setTotal((long) medecins.size());
        List<String> prioritySegments = controleService.getEtatsActifs(TypeControle.SEGEMENTS);
        if (prioritySegments.size() >= 2) {
            prioritySegments = prioritySegments.subList(0, prioritySegments.size() - 1);
        }
        List<String> eligibleSegments = prioritySegments;
        kpis.setSegmentsAB(medecins.stream().filter(m -> eligibleSegments.contains(m.getSegment())).count());
        kpis.setSurveillance(medecins.stream().filter(m -> "SURVEILLANCE".equalsIgnoreCase(m.getStatut())).count());
        kpis.setOnboarding(medecins.stream().filter(m -> "ONBOARDING".equalsIgnoreCase(m.getStatut())).count());
        kpis.setSilenceCritique(medecins.stream().filter(m -> "SILENCE_CRITIQUE".equalsIgnoreCase(m.getStatut())).count());
        kpis.setActionsEnCours(actionService.countPlanifiees());
        kpis.setSansNoteInput(medecins.stream().filter(m -> m.getNoteInput() == null).count());
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
            case "ACTIF_STABLE", "PROGRESSION", "SURVEILLANCE", "RETENTION", "ONBOARDING", "ACTIVITE_IRREGULIERE", "A_REACTIVER", "INACTIF", "ACTIF" -> StatutPilotage.valueOf(normalized);
            default -> StatutPilotage.ACTIF_STABLE;
        };
    }

    // Recalcule dynamiquement les statuts (selon les règles Controle) et segments (A/B/C/D) de tous les médecins
    public void recalculerStatutsEtSegmentsDynamiques() {
        // Utilise la fin du dernier mois avec des données en base,
        // et non la date du jour, pour ne pas analyser un mois courant vide.
        List<java.time.LocalDate> dates = extractionDonneesRepository.findAllDatesDescending();
        java.time.LocalDate dateRef = dates.isEmpty()
                ? java.time.LocalDate.now()
                : java.time.YearMonth.from(dates.get(0)).atEndOfMonth();
        recalculerStatutsEtSegmentsDynamiques(dateRef);
    }

    public void recalculerStatutsEtSegmentsDynamiques(LocalDate dateReference) {
        List<Medecin> medecins = medecinsEligibles();
        if (medecins.isEmpty()) return;

        YearMonth ymM = YearMonth.from(dateReference);
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
            m.setIsProfilIrregulier(false);
            String key = String.valueOf(m.getId());
            long valM = caM.getOrDefault(key, 0L);
            long valMm1 = caMm1.getOrDefault(key, 0L);
                boolean hasExtractionHistory = totalCasMap.containsKey(m.getId())
                    || caM.containsKey(key) || caMm1.containsKey(key)
                    || caMm2.containsKey(key) || caMm3.containsKey(key)
                    || casM.containsKey(key) || casMm1.containsKey(key)
                    || casMm2.containsKey(key) || casMm3.containsKey(key);

                m.setCaMois(hasExtractionHistory ? (int) valM : null);
                m.setCaBaseline(hasExtractionHistory ? (int) valMm1 : null);
                m.setTotalCas(totalCasMap.containsKey(m.getId())
                    ? totalCasMap.get(m.getId()).intValue() : null);

            String statutDynamique;
            boolean actif60Jours = StatutMensuelPolicy.activiteRecente(m.getDateDerniereActivite(), dateReference);

            if (Boolean.TRUE.equals(m.getIsAReactiverManuel()) && valM == 0) {
                statutDynamique = "A_REACTIVER";
            } else if (valM == 0) {
                statutDynamique = actif60Jours ? "RETENTION" : "INACTIF";
            } else if (valM > 0 && estOnboardingRecent(m, dateReference)) {
                statutDynamique = "ONBOARDING";
            } else {
                double caReference = average(caMm1, caMm2, caMm3, key);
                double casReference = average(casMm1, casMm2, casMm3, key);
                double variationCa = ((valM - caReference) / Math.max(caReference, 300.0)) * 100.0;
                double variationCas = ((casM.getOrDefault(key, 0L) - casReference)
                        / Math.max(casReference, 1.0)) * 100.0;
                double variationMixte = StatutMensuelPolicy.variationMixte(variationCa, variationCas);
                statutDynamique = StatutMensuelPolicy.statutDepuisVariation(variationMixte);

            }

            if (("SURVEILLANCE".equals(statutDynamique) || "RETENTION".equals(statutDynamique))
                    && activitePortefeuilleService.estProfilActiviteIrreguliere(m, statutDynamique, dateReference)) {
                m.setIsProfilIrregulier(true);
                statutDynamique = "ACTIVITE_IRREGULIERE";
            }

            m.setCaMois(hasExtractionHistory ? (int) valM : null);
            m.setCaBaseline(hasExtractionHistory ? (int) valMm1 : null);

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
        segmentationService.recalculerSegmentationPortefeuille(dateReference);
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

    private boolean estOnboardingRecent(Medecin medecin, LocalDate dateReference) {
        LocalDate premiereActivite = medecin.getDatePremiereCollaboration();
        if (premiereActivite == null) {
            premiereActivite = extractionDonneesRepository.findDatesReceptionByMedecinId(medecin.getId())
                    .stream().min(LocalDate::compareTo).orElse(null);
        }
        if (premiereActivite == null) return false;
        return StatutMensuelPolicy.onboardingRecent(premiereActivite, dateReference);
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
        meta.setCharges((long) medecinsEligibles().size());

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

    private List<Medecin> medecinsEligibles() {
        return medecinRepository.findAllWithImportedDossiers().stream()
                .filter(m -> !medecinRepository.isPseudoMedecin(m)).toList();
    }
}
