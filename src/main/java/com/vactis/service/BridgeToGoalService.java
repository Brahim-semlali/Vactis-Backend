package com.vactis.service;

import com.vactis.dto.bridgetogoal.BridgeToGoalResponse;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.reclamation.StatutReclamation;
import com.vactis.model.system.SystemSettings;
import com.vactis.repository.ActionRepository;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.ReclamationRepository;
import com.vactis.service.system.SystemSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public BridgeToGoalResponse simulerTrajectoire(Long targetBudgetParam, String moisParam) {
        YearMonth ym = determinerMoisReference(moisParam);

        Long caActuel = extractionDonneesRepository.sumPrixAPayerByDateRange(ym.atDay(1), ym.atEndOfMonth());
        if (caActuel == null) caActuel = 0L;
        Long casMoisTotal = extractionDonneesRepository.countCasByDateRange(ym.atDay(1), ym.atEndOfMonth());
        Long caPortefeuille = extractionDonneesRepository.sumPrixAPayerWithMedecinByDateRange(ym.atDay(1), ym.atEndOfMonth());
        Long nonAffectesCount = extractionDonneesRepository.countNonAffectesByDateRange(ym.atDay(1), ym.atEndOfMonth());
        Long medecinsIdentifies = extractionDonneesRepository.countMedecinsDistinctsByDateRange(ym.atDay(1), ym.atEndOfMonth());
        if (casMoisTotal == null) casMoisTotal = 0L;
        if (caPortefeuille == null) caPortefeuille = 0L;
        if (nonAffectesCount == null) nonAffectesCount = 0L;
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
        long runRateAnnuel = caMoyenMensuel * 12L;

        List<Medecin> allMedecins = medecinRepository.findAll();

        int nbOnboarding = 0;
        int nbRetention = 0;
        int nbDeveloppement = 0;
        int nbSegmentA = 0;
        int nbSegmentB = 0;
        int nbSegmentC = 0;
        int nbSegmentD = 0;
        Map<String, Long> caParSegment = new HashMap<>();
        Map<String, Integer> medecinsParSegment = new HashMap<>();

        for (Object[] row : extractionDonneesRepository.sumCaByMedecinAndDateRange(ym.atDay(1), ym.atEndOfMonth())) {
            Long medecinId = ((Number) row[0]).longValue();
            long ca = ((Number) row[1]).longValue();
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

            if ("ONBOARDING".equals(st)) {
                nbOnboarding++;
            } else if ("RETENTION".equals(st) || "SURVEILLANCE".equals(st)) {
                nbRetention++;
            } else if (("A".equals(seg) || "B".equals(seg)) && ("ACTIF_STABLE".equals(st) || "PROGRESSION".equals(st) || "ACTIF".equals(st))) {
                nbDeveloppement++;
            }
        }

        long nbReclamationsBloquantes = reclamationRepository.countByStatut(StatutReclamation.OUVERTE);

        // Modélisation des composantes du Bridge
        // 1. Gain Onboarding : estimation progressive (environ 1 200 € par médecin onboarding)
        long gainOnboarding = nbOnboarding * 1200L;

        // 2. Gain Rétention : valeur sauvée grâce aux plans d'action (environ 1 800 € par médecin sécurisé)
        long gainRetention = Math.round(nbRetention * 1800.0 * 0.60);

        // 3. Gain Développement : levier sur Segments A & B (environ 800 € par compte A/B)
        long gainDeveloppement = nbDeveloppement * 800L;

        // 4. Churn Estimé : attrition naturelle et risques non couverts + impact réclamations
        long churnEstime = Math.round((nbRetention * 1800.0 * 0.40) + (nbReclamationsBloquantes * 1500.0));

        // CA Projeté
        long caProjete = caActuel + gainOnboarding + gainRetention + gainDeveloppement - churnEstime;
        if (caProjete < 0) caProjete = 0L;

        // Définition de la target budgétaire : priorité à l'objectif saisi, puis à l'objectif global admin, puis au calcul par défaut.
        Long configuredTarget = null;
        if (systemSettingsService != null) {
            SystemSettings settings = systemSettingsService.getSettings();
            if (settings != null && settings.getBridgeGoalTarget() != null && settings.getBridgeGoalTarget() > 0) {
                configuredTarget = settings.getBridgeGoalTarget();
            }
        }

        long targetBudget = (targetBudgetParam != null && targetBudgetParam > 0)
                ? targetBudgetParam
                : (configuredTarget != null ? configuredTarget : Math.round(caActuel > 0 ? caActuel * 1.12 : 50000L));

        long gap = Math.max(0L, targetBudget - caProjete);
        long objectifYtdProratise = Math.round((double) targetBudget * moisEcoules / 12.0);
        long varianceYtd = caYtdReel - objectifYtdProratise;
        double varianceYtdPct = objectifYtdProratise > 0
            ? Math.round((double) varianceYtd / objectifYtdProratise * 1000.0) / 10.0
            : 0.0;
        long gapProjete = Math.max(0L, targetBudget - runRateAnnuel);
        long effortAdditionnelMensuel = moisRestants > 0
            ? Math.round((double) gapProjete / moisRestants)
            : gapProjete;

        long potentielRestant = Math.max(0L, gapProjete - (gainOnboarding + gainRetention + gainDeveloppement));
        long caMoyenSegmentA = moyenneSegment(caParSegment, medecinsParSegment, "A");
        long caMoyenSegmentB = moyenneSegment(caParSegment, medecinsParSegment, "B");
        long caMoyenSegmentC = moyenneSegment(caParSegment, medecinsParSegment, "C");
        long caMoyenSegmentD = moyenneSegment(caParSegment, medecinsParSegment, "D");

        // Actions requises : estimation basée sur 1 500 € de CA incrémental moyen par action menée avec succès
        int actionsRequises = gap > 0 ? (int) Math.ceil((double) gap / 1500.0) : 0;

        double tauxAtteinte = targetBudget > 0 ? Math.round(((double) caProjete / targetBudget * 100.0) * 10.0) / 10.0 : 100.0;

        String statutTrajectoire;
        if (gap == 0) {
            statutTrajectoire = "ATTEINT";
        } else if (caProjete >= (targetBudget * 0.85)) {
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
                .nbMedecinsSegmentA(nbSegmentA)
                .nbMedecinsSegmentB(nbSegmentB)
                .nbMedecinsSegmentC(nbSegmentC)
                .nbMedecinsSegmentD(nbSegmentD)
                .nonAffectesCount(nonAffectesCount)
                .medecinsIdentifies(medecinsIdentifies)
                .caMoyenSegmentA(caMoyenSegmentA)
                .caMoyenSegmentB(caMoyenSegmentB)
                .caMoyenSegmentC(caMoyenSegmentC)
                .caMoyenSegmentD(caMoyenSegmentD)
                .nouveauxMedecinsSegmentA(estimerNouveauxMedecins(potentielRestant, caMoyenSegmentA))
                .nouveauxMedecinsSegmentB(estimerNouveauxMedecins(potentielRestant, caMoyenSegmentB))
                .nouveauxMedecinsSegmentC(estimerNouveauxMedecins(potentielRestant, caMoyenSegmentC))
                .nouveauxMedecinsSegmentD(estimerNouveauxMedecins(potentielRestant, caMoyenSegmentD))
                .build();
    }

    private long moyenneSegment(Map<String, Long> caParSegment, Map<String, Integer> medecinsParSegment, String segment) {
        int count = medecinsParSegment.getOrDefault(segment, 0);
        return count > 0 ? Math.round((double) caParSegment.getOrDefault(segment, 0L) / count) : 0L;
    }

    private int estimerNouveauxMedecins(long gap, long caMoyenMensuel) {
        long caAvecMonteeEnCharge = Math.round(caMoyenMensuel * 0.50);
        return gap > 0 && caAvecMonteeEnCharge > 0
                ? (int) Math.ceil((double) gap / caAvecMonteeEnCharge)
                : 0;
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
