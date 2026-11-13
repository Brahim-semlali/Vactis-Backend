package com.vactis.service;

import com.vactis.dto.bridgetogoal.BridgeToGoalResponse;
import com.vactis.dto.bridgetogoal.BridgeOpportunityItem;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.reclamation.StatutReclamation;
import com.vactis.model.system.SystemSettings;
import com.vactis.model.system.BridgeGoalTarget;
import com.vactis.repository.ActionRepository;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.ReclamationRepository;
import com.vactis.repository.system.BridgeGoalTargetRepository;
import com.vactis.service.system.SystemSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Service de simulation de trajectoire budgétaire "Bridge to Goal" (Sections 12 & 13).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BridgeToGoalService {

    private final MedecinRepository medecinRepository;
    private final ExtractionDonneesRepository extractionDonneesRepository;
    private final ActionRepository actionRepository;
    private final ReclamationRepository reclamationRepository;
    private final SystemSettingsService systemSettingsService;
    private final BridgeGoalTargetRepository bridgeGoalTargetRepository;

    public BridgeToGoalResponse simulerTrajectoire(String moisParam) {
        YearMonth ym = determinerMoisReference(moisParam);
        BridgeGoalTarget monthlyTarget = bridgeGoalTargetRepository.findByPeriod(ym.toString()).orElse(null);
        SystemSettings settings = systemSettingsService.getSettings();
        Long targetBudget = settings == null ? null : settings.getBridgeGoalTarget();
        if ((targetBudget == null || targetBudget <= 0) && monthlyTarget == null) {
            throw new ResponseStatusException(
                HttpStatus.PRECONDITION_FAILED,
                "L’objectif annuel doit être défini par un administrateur dans les paramètres système."
            );
        }
        if (targetBudget == null || targetBudget <= 0) targetBudget = 0L;
        long proratedMonthlyTarget = Math.round((double) targetBudget / 12.0);
        long effectiveMonthlyTarget = monthlyTarget == null ? proratedMonthlyTarget : monthlyTarget.getTargetAmount();
        Long previousMonthRevenue = extractionDonneesRepository.sumPrixAPayerByDateRange(
            ym.minusMonths(1).atDay(1), ym.minusMonths(1).atEndOfMonth());
        if (previousMonthRevenue == null) previousMonthRevenue = 0L;

        Long caActuel = extractionDonneesRepository.sumPrixAPayerByDateRange(ym.atDay(1), ym.atEndOfMonth());
        if (caActuel == null) caActuel = 0L;
        Long casMoisTotal = extractionDonneesRepository.countCasByDateRange(ym.atDay(1), ym.atEndOfMonth());
        List<Medecin> allMedecins = medecinRepository.findAllWithImportedDossiers().stream()
            .filter(m -> !medecinRepository.isPseudoMedecin(m))
            .toList();
        List<Long> medecinIdsIdentifiables = allMedecins.stream().map(Medecin::getId).filter(Objects::nonNull).toList();
        Long caPortefeuille = medecinIdsIdentifiables.isEmpty() ? 0L
            : extractionDonneesRepository.sumPrixAPayerForIdentifiedDoctorsByDateRange(ym.atDay(1), ym.atEndOfMonth(), medecinIdsIdentifiables);
        Long nonAffectesCount = medecinIdsIdentifiables.isEmpty() ? casMoisTotal
            : extractionDonneesRepository.countCasesForUnidentifiedDoctorsByDateRange(ym.atDay(1), ym.atEndOfMonth(), medecinIdsIdentifiables);
        Long caNonAffectes = medecinIdsIdentifiables.isEmpty() ? caActuel
            : extractionDonneesRepository.sumPrixAPayerForUnidentifiedDoctorsByDateRange(ym.atDay(1), ym.atEndOfMonth(), medecinIdsIdentifiables);
        Long medecinsIdentifies = medecinIdsIdentifiables.isEmpty() ? 0L
            : extractionDonneesRepository.countIdentifiedDoctorsByDateRange(ym.atDay(1), ym.atEndOfMonth(), medecinIdsIdentifiables);
        if (casMoisTotal == null) casMoisTotal = 0L;
        if (caPortefeuille == null) caPortefeuille = 0L;
        if (nonAffectesCount == null) nonAffectesCount = 0L;
        if (caNonAffectes == null) caNonAffectes = 0L;
        if (medecinsIdentifies == null) medecinsIdentifies = 0L;

        LocalDate ytdStart = LocalDate.of(ym.getYear(), 1, 1);
        LocalDate ytdEnd = ym.atEndOfMonth();
        Long caYtdReel = extractionDonneesRepository.sumPrixAPayerByDateRange(ytdStart, ytdEnd);
        Long caNMoins1Comparable = extractionDonneesRepository.sumPrixAPayerByDateRange(
            ytdStart.minusYears(1), ytdEnd.minusYears(1));
        if (caYtdReel == null) caYtdReel = 0L;
        if (caNMoins1Comparable == null) caNMoins1Comparable = 0L;
        int moisEcoules = ym.getMonthValue();
        int moisRestants = 12 - moisEcoules;
        long caMoyenMensuel = moisEcoules > 0 ? Math.round((double) caYtdReel / moisEcoules) : 0L;
        long runRateAnnuel = BridgeToGoalPolicy.runRateAnnuel(caYtdReel, moisEcoules);

        int nbOnboarding = 0;
        int nbRetention = 0;
        int nbDeveloppement = 0;
        int nbSegmentA = 0;
        int nbSegmentB = 0;
        int nbSegmentC = 0;
        int nbSegmentD = 0;
        Map<String, Long> caParSegment = new HashMap<>();
        Map<String, Integer> medecinsParSegment = new HashMap<>();
        Map<Long, Long> caParMedecin = new HashMap<>();
        Map<String, Map<String, Integer>> opportunitesParTypeEtSegment = new HashMap<>();
        Map<String, Map<String, Long>> caParTypeEtSegment = new HashMap<>();

        for (Object[] row : extractionDonneesRepository.sumCaByMedecinAndDateRange(ym.atDay(1), ym.atEndOfMonth())) {
            Long medecinId = ((Number) row[0]).longValue();
            long ca = ((Number) row[1]).longValue();
            caParMedecin.put(medecinId, ca);
            allMedecins.stream().filter(m -> medecinId.equals(m.getId())).findFirst().ifPresent(m -> {
                String segment = m.getSegment() != null ? m.getSegment().toUpperCase() : "D";
                caParSegment.merge(segment, ca, Long::sum);
            });
        }

        for (Medecin m : allMedecins) {
            String st = m.getStatut() != null ? m.getStatut().toUpperCase() : "INACTIF";
            String seg = m.getSegment() != null ? m.getSegment().toUpperCase() : "D";

            switch (seg) {
                case "A" -> nbSegmentA++;
                case "B" -> nbSegmentB++;
                case "C" -> nbSegmentC++;
                default -> nbSegmentD++;
            }
            medecinsParSegment.merge(seg, 1, Integer::sum);

            long caMedecin = caParMedecin.getOrDefault(m.getId(), 0L);
            if ("ONBOARDING".equals(st)) {
                ajouterOpportunite(opportunitesParTypeEtSegment, caParTypeEtSegment, "ONBOARDING", seg, caMedecin);
            }
            if ("RETENTION".equals(st) || "SURVEILLANCE".equals(st)) {
                ajouterOpportunite(opportunitesParTypeEtSegment, caParTypeEtSegment, "RETENTION", seg, caMedecin);
            }
            if (m.getRisqueUrgence() == com.vactis.model.medecin.RisqueUrgence.URGENT
                    && ("A".equals(seg) || "B".equals(seg))) {
                ajouterOpportunite(opportunitesParTypeEtSegment, caParTypeEtSegment, "SILENCE", seg, caMedecin);
            }
            if (("A".equals(seg) || "B".equals(seg))
                    && ("ACTIF_STABLE".equals(st) || "PROGRESSION".equals(st) || "ACTIF".equals(st))) {
                ajouterOpportunite(opportunitesParTypeEtSegment, caParTypeEtSegment, "DEVELOPPEMENT", seg, caMedecin);
            }

            if ("ONBOARDING".equals(st)) {
                nbOnboarding++;
            } else if ("RETENTION".equals(st) || "SURVEILLANCE".equals(st)) {
                nbRetention++;
            } else if (("A".equals(seg) || "B".equals(seg)) && ("ACTIF_STABLE".equals(st) || "PROGRESSION".equals(st) || "ACTIF".equals(st))) {
                nbDeveloppement++;
            }
        }

        long nbReclamationsBloquantes = reclamationRepository.countByStatut(StatutReclamation.A_TRAITER)
            + reclamationRepository.countByStatut(StatutReclamation.OUVERTE);

        // Modélisation des composantes du Bridge
        // 1. Gain Onboarding : estimation progressive (environ 1 200 € par médecin onboarding)
        long gainOnboarding = nbOnboarding * 1200L;

        // 2. Gain Rétention : valeur sauvée grâce aux plans d'action (environ 1 800 € par médecin sécurisé)
        long gainRetention = Math.round(nbRetention * 1800.0 * 0.60);

        // 3. Gain Développement : levier sur Segments A & B (environ 800 € par compte A/B)
        long gainDeveloppement = nbDeveloppement * 800L;

        List<BridgeOpportunityItem> opportunites = new ArrayList<>();
        ajouterLignesOpportunite(opportunites, opportunitesParTypeEtSegment, caParTypeEtSegment,
            "RETENTION", "Rétention / baisse activité", gainRetention,
            "Potentiel théorique à confirmer après diagnostic terrain.");
        ajouterLignesOpportunite(opportunites, opportunitesParTypeEtSegment, caParTypeEtSegment,
            "SILENCE", "Silence élevé / critique Segment A/B", 0L,
            "Signal de rupture à qualifier ; ce bloc n'ajoute pas de potentiel au total.");
        ajouterLignesOpportunite(opportunites, opportunitesParTypeEtSegment, caParTypeEtSegment,
            "DEVELOPPEMENT", "Sous-exploitation / développement", gainDeveloppement,
            "Opportunité interne estimée à confirmer sur le terrain.");
        ajouterLignesOpportunite(opportunites, opportunitesParTypeEtSegment, caParTypeEtSegment,
            "ONBOARDING", "Onboarding / qualification", gainOnboarding,
            "Potentiel d'intégration à qualifier avec le commercial.");

        // 4. Churn Estimé : attrition naturelle et risques non couverts + impact réclamations
        long churnEstime = Math.round((nbRetention * 1800.0 * 0.40) + (nbReclamationsBloquantes * 1500.0));

        // CA Projeté
        long caProjete = caActuel + gainOnboarding + gainRetention + gainDeveloppement - churnEstime;
        if (caProjete < 0) caProjete = 0L;

        long gap = Math.max(0L, effectiveMonthlyTarget - caProjete);
        long objectifYtdProratise = Math.round((double) targetBudget * moisEcoules / 12.0);
        long varianceYtd = caYtdReel - objectifYtdProratise;
        double varianceYtdPct = objectifYtdProratise > 0
            ? Math.round((double) varianceYtd / objectifYtdProratise * 1000.0) / 10.0
            : 0.0;
        long gapProjete = BridgeToGoalPolicy.gapProjete(targetBudget, runRateAnnuel);
        long effortAdditionnelMensuel = BridgeToGoalPolicy.effortMensuel(gapProjete, moisRestants);

        long potentielRestant = Math.max(0L, gapProjete - (gainOnboarding + gainRetention + gainDeveloppement));
        double couverturePotentiel = BridgeToGoalPolicy.couverturePourcent(
            gainOnboarding + gainRetention + gainDeveloppement, gapProjete);
        long caMoyenSegmentA = moyenneSegment(caParSegment, medecinsParSegment, "A");
        long caMoyenSegmentB = moyenneSegment(caParSegment, medecinsParSegment, "B");
        long caMoyenSegmentC = moyenneSegment(caParSegment, medecinsParSegment, "C");
        long caMoyenSegmentD = moyenneSegment(caParSegment, medecinsParSegment, "D");

        // Actions requises : estimation basée sur 1 500 € de CA incrémental moyen par action menée avec succès
        int actionsRequises = gap > 0 ? (int) Math.ceil((double) gap / 1500.0) : 0;

        long comparisonTarget = effectiveMonthlyTarget;
        double tauxAtteinte = comparisonTarget > 0 ? Math.round(((double) caProjete / comparisonTarget * 100.0) * 10.0) / 10.0 : 100.0;

        String statutTrajectoire;
        if (gap == 0) {
            statutTrajectoire = "ATTEINT";
        } else if (caProjete >= (comparisonTarget * 0.85)) {
            statutTrajectoire = "TENDU";
        } else {
            statutTrajectoire = "CRITIQUE";
        }

        return BridgeToGoalResponse.builder()
                .periode(ym.toString())
                .caActuel(caActuel)
                .gainOnboarding(gainOnboarding)
                .gainRetention(gainRetention)
                .gainDeveloppement(gainDeveloppement)
                .churnEstime(churnEstime)
                .caProjete(caProjete)
                .targetBudget(targetBudget)
                .annualTargetBudget(targetBudget)
                .monthlyTargetBudget(effectiveMonthlyTarget)
                .previousMonthRevenue(previousMonthRevenue)
                .monthlyTargetMethod(monthlyTarget == null ? null : monthlyTarget.getMethod())
                .monthlyTargetInputValue(monthlyTarget == null ? null : monthlyTarget.getInputValue().doubleValue())
                .gap(gap)
                .actionsRequises(actionsRequises)
                .statutTrajectoire(statutTrajectoire)
                .tauxAtteinte(tauxAtteinte)
                .nbMedecinsOnboarding(nbOnboarding)
                .nbMedecinsEnRetention(nbRetention)
                .nbMedecinsDeveloppement(nbDeveloppement)
                .nbReclamationsBloquantes((int) nbReclamationsBloquantes)
                .caMoisTotal(caActuel)
                .casMoisTotal(casMoisTotal)
                .caPortefeuille(caPortefeuille)
                .nbMedecinsActifs(allMedecins.size())
                .caYtdReel(caYtdReel)
                .moisEcoules(moisEcoules)
                .caNMoins1Comparable(caNMoins1Comparable)
                .caMoyenMensuel(caMoyenMensuel)
                .objectifYtdProratise(objectifYtdProratise)
                .varianceYtd(varianceYtd)
                .varianceYtdPct(varianceYtdPct)
                .runRateAnnuel(runRateAnnuel)
                .gapProjete(gapProjete)
                .moisRestants(moisRestants)
                .effortAdditionnelMensuel(effortAdditionnelMensuel)
                .statutYtd(varianceYtd >= 0 ? "EN AVANCE" : (varianceYtdPct >= -10.0 ? "RETARD MODERE" : "RETARD IMPORTANT"))
                .decisionCouverture(BridgeToGoalPolicy.decisionCouverture(couverturePotentiel))
                .nbMedecinsSegmentA(nbSegmentA)
                .nbMedecinsSegmentB(nbSegmentB)
                .nbMedecinsSegmentC(nbSegmentC)
                .nbMedecinsSegmentD(nbSegmentD)
                .nonAffectesCount(nonAffectesCount)
                .caNonAffectes(caNonAffectes)
                .medecinsIdentifies(medecinsIdentifies)
                .caMoyenSegmentA(caMoyenSegmentA)
                .caMoyenSegmentB(caMoyenSegmentB)
                .caMoyenSegmentC(caMoyenSegmentC)
                .caMoyenSegmentD(caMoyenSegmentD)
                .nouveauxMedecinsSegmentA(estimerNouveauxMedecins(potentielRestant, caMoyenSegmentA))
                .nouveauxMedecinsSegmentB(estimerNouveauxMedecins(potentielRestant, caMoyenSegmentB))
                .nouveauxMedecinsSegmentC(estimerNouveauxMedecins(potentielRestant, caMoyenSegmentC))
                .nouveauxMedecinsSegmentD(estimerNouveauxMedecins(potentielRestant, caMoyenSegmentD))
                .opportunites(opportunites)
                .build();
    }

            private void ajouterOpportunite(
                Map<String, Map<String, Integer>> comptes,
                Map<String, Map<String, Long>> chiffresAffaires,
                String type,
                String segment,
                long ca
            ) {
            comptes.computeIfAbsent(type, ignored -> new HashMap<>()).merge(segment, 1, Integer::sum);
            chiffresAffaires.computeIfAbsent(type, ignored -> new HashMap<>()).merge(segment, ca, Long::sum);
            }

            private void ajouterLignesOpportunite(
                List<BridgeOpportunityItem> resultat,
                Map<String, Map<String, Integer>> comptes,
                Map<String, Map<String, Long>> chiffresAffaires,
                String type,
                String libelle,
                long potentielTotal,
                String description
            ) {
            Map<String, Integer> parSegment = comptes.getOrDefault(type, Map.of());
            int totalMedecins = parSegment.values().stream().mapToInt(Integer::intValue).sum();
            if (totalMedecins == 0) return;

            List<String> segments = List.of("A", "B", "C", "D").stream()
                .filter(parSegment::containsKey)
                .toList();
            long potentielDistribue = 0L;
            for (int index = 0; index < segments.size(); index++) {
                String segment = segments.get(index);
                int medecins = parSegment.get(segment);
                long partPotentiel = index == segments.size() - 1
                    ? potentielTotal - potentielDistribue
                    : Math.round((double) potentielTotal * medecins / totalMedecins);
                potentielDistribue += partPotentiel;
                resultat.add(new BridgeOpportunityItem(
                    type,
                    libelle,
                    segment,
                    medecins,
                    chiffresAffaires.getOrDefault(type, Map.of()).getOrDefault(segment, 0L),
                    partPotentiel,
                    description));
            }
            }

    private long moyenneSegment(Map<String, Long> caParSegment, Map<String, Integer> medecinsParSegment, String segment) {
        int count = medecinsParSegment.getOrDefault(segment, 0);
        return count > 0 ? Math.round((double) caParSegment.getOrDefault(segment, 0L) / count) : 0L;
    }

    private int estimerNouveauxMedecins(long gap, long caMoyenMensuel) {
        return BridgeToGoalPolicy.nouveauxMedecinsNecessaires(gap, caMoyenMensuel, 0.50);
    }

    private YearMonth determinerMoisReference(String moisParam) {
        if (moisParam != null && !moisParam.isBlank()) {
            try {
                return YearMonth.parse(moisParam);
            } catch (Exception ignored) {
            }
        }
        List<LocalDate> dates = extractionDonneesRepository.findAllDatesDescending();
        if (dates != null && !dates.isEmpty()) {
            return YearMonth.from(dates.get(0));
        }
        return YearMonth.now();
    }
}
