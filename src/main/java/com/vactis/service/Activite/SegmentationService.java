package com.vactis.service.Activite;

import com.vactis.model.Controle.TypeControle;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.RetourTerrain;
import com.vactis.model.medecin.RisqueUrgence;
import com.vactis.model.medecin.StatutVisite;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.RetourTerrainRepository;
import com.vactis.service.ControleService;
import com.vactis.service.RetourTerrainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class SegmentationService {

    private final MedecinRepository medecinRepository;
    private final ExtractionDonneesRepository extractionDonneesRepository;
    private final RetourTerrainRepository retourTerrainRepository;
    private final ControleService controleService;
    private final RetourTerrainService retourTerrainService;

    @Transactional
    public void recalculerSegmentationPortefeuille() {
        List<Medecin> medecins = medecinRepository.findAll();
        if (medecins.isEmpty()) return;

        int totalMedecins = medecins.size();

        List<LocalDate> datesReception = extractionDonneesRepository.findAllDatesDescending();
        YearMonth moisCourant = datesReception.isEmpty() ? YearMonth.now() : YearMonth.from(datesReception.get(0));
        Map<YearMonth, Map<Long, Double>> caParMois = new HashMap<>();
        Map<YearMonth, Map<Long, Double>> volumeParMois = new HashMap<>();
        for (int offset = 0; offset <= 3; offset++) {
            YearMonth mois = moisCourant.minusMonths(offset);
            caParMois.put(mois, aggregateByMedecin(
                extractionDonneesRepository.sumCaByMedecinAndDateRange(mois.atDay(1), mois.atEndOfMonth())));
            volumeParMois.put(mois, aggregateByMedecin(
                extractionDonneesRepository.countCasByMedecinAndDateRange(mois.atDay(1), mois.atEndOfMonth())));
        }

        Map<Long, Double> caMoyenParMedecin = new HashMap<>();
        for (Medecin medecin : medecins) {
            double caMoyen = 0.0;
            for (int offset = 0; offset < 3; offset++) {
                caMoyen += valueFor(caParMois, moisCourant.minusMonths(offset), medecin.getId());
            }
            caMoyenParMedecin.put(medecin.getId(), caMoyen / 3.0);
        }

        // 1. Récupérer le nombre de cas par médecin
        Map<Long, Double> volumeCourantParMedecin = volumeParMois.get(moisCourant);
        Map<Long, Long> casMap = new HashMap<>();
        for (Map.Entry<Long, Double> entry : volumeCourantParMedecin.entrySet()) {
            casMap.put(entry.getKey(), entry.getValue().longValue());
        }

        // 2. Calculer le CA max et le Nombre de cas max dans le portefeuille pour la normalisation
        double maxCa = medecins.stream()
            .mapToDouble(m -> valueFor(caParMois, moisCourant, m.getId()))
                .max()
                .orElse(0.0);
        if (maxCa <= 0) maxCa = 1.0;

        double maxCas = medecins.stream()
                .mapToDouble(m -> casMap.getOrDefault(m.getId(), 0L).doubleValue())
                .max()
                .orElse(0.0);
        if (maxCas <= 0) maxCas = 1.0;

        // 3. Trier par CA mensuel moyen croissant pour déterminer le rang percentile (Performance)
        List<Medecin> medecinsTriesParCa = new ArrayList<>(medecins);
        medecinsTriesParCa.sort(Comparator.comparingDouble(m -> caMoyenParMedecin.getOrDefault(m.getId(), 0.0)));

        Map<Long, Double> performanceMap = new HashMap<>();
        for (int i = 0; i < totalMedecins; i++) {
            Medecin m = medecinsTriesParCa.get(i);
            double performance = (totalMedecins == 1) ? 100.0 : ((double) i / (totalMedecins - 1)) * 100.0;
            m.setRangPerformance(i + 1);
            m.setTotalPortefeuillePerformance(totalMedecins);
            performanceMap.put(m.getId(), performance);
        }

        // 4. Calculer le Score de valeur et attribuer le segment pour chaque médecin
        for (Medecin m : medecins) {
            // A. Potentiel (40%) : note terrain prioritaire > note saisie dans Médecins > note neutre
            double noteSur5 = 3.0;
            Optional<RetourTerrain> dernierRetour = retourTerrainService.getDerniereVisite(m)
                .filter(retour -> retour.getStatutVisite() == StatutVisite.REALISEE);
            if (dernierRetour.isPresent() && dernierRetour.get().getNote() != null) {
                noteSur5 = dernierRetour.get().getNote();
                m.setSourcePotentiel("NOTE_TERRAIN");
            } else if (m.getNoteInput() != null) {
                noteSur5 = m.getNoteInput();
                m.setSourcePotentiel("INPUT_PROFIL");
            } else {
                m.setSourcePotentiel("DEFAUT");
            }
            double potentielSur100 = Math.min(100.0, Math.max(0.0, (noteSur5 / 5.0) * 100.0));
            m.setPotentielSur100(potentielSur100);

            double caCourant = valueFor(caParMois, moisCourant, m.getId());
            double volumeCourant = valueFor(volumeParMois, moisCourant, m.getId());
            double caReference = averageForPreviousMonths(caParMois, m, moisCourant);
            double volumeReference = averageForPreviousMonths(volumeParMois, m, moisCourant);
            double variationCa = percentageDifference(caCourant, caReference, 300.0);
            double variationVolume = percentageDifference(volumeCourant, volumeReference, 1.0);
            double variationMixte = (0.60 * variationCa) + (0.40 * variationVolume);
            m.setReferenceCa(round(caReference));
            m.setReferenceVolume(round(volumeReference));
            m.setVariationCa(round(variationCa));
            m.setVariationVolume(round(variationVolume));
            m.setVariationMixteSur100(round(variationMixte));

            // B. Performance (40%) : Rang percentile CA dans le portefeuille
            double performance = performanceMap.getOrDefault(m.getId(), 0.0);
            m.setCaMensuelMoyen(round(caMoyenParMedecin.getOrDefault(m.getId(), 0.0)));
            m.setPerformanceSur100(performance);

            // C. Poids économique (20%) : 50% CA normalisé + 50% Cas normalisé
            double caPhys = caCourant;
            double casPhys = casMap.getOrDefault(m.getId(), 0L).doubleValue();

            double caNormalise = (caPhys / maxCa) * 100.0;
            double casNormalise = (casPhys / maxCas) * 100.0;
            double poidsEconomique = (0.50 * caNormalise) + (0.50 * casNormalise);
            m.setCaNormaliseSur100(round(caNormalise));
            m.setVolumeNormaliseSur100(round(casNormalise));
            m.setMaxCaPortefeuille(round(maxCa));
            m.setMaxVolumePortefeuille(round(maxCas));
            m.setPoidsEcoSur100(poidsEconomique);

            List<LocalDate> datesMedecin = extractionDonneesRepository.findDatesReceptionByMedecinId(m.getId());
            int intervalleEffectif = calculateIntervalleEffectif(datesMedecin);
            m.setIntervalleEffectif(intervalleEffectif);
                long moisActifs = datesMedecin.stream()
                    .map(YearMonth::from)
                    .distinct()
                    .count();
                boolean ancienneteSuffisante = m.getDatePremiereCollaboration() != null
                    && !m.getDatePremiereCollaboration().plusMonths(6).isAfter(LocalDate.now());
                m.setFiabilite(moisActifs >= 3 && ancienneteSuffisante
                    ? "FIABLE"
                    : moisActifs >= 2 ? "PARTIEL" : "NON_FIABLE");
            int joursSansActivite = calculateDaysSinceLastActivity(datesMedecin);
                m.setJoursSansActivite(joursSansActivite);
            double scoreSilence = Math.min(100.0, (joursSansActivite / (double) intervalleEffectif) * 20.0);
            double baisseReference = Math.max(0.0, -variationCa);
                double baisseCourte = Math.max(0.0, -percentageDifference(caCourant,
                    valueFor(caParMois, moisCourant.minusMonths(1), m.getId()), 300.0));
                m.setBaisseReference(round(baisseReference));
                m.setBaisseCourte(round(baisseCourte));
            double scoreRisque = Math.min(100.0, (0.40 * baisseReference) + (0.60 * baisseCourte));
            m.setScoreSilence(round(scoreSilence));
            double risquePondere = (0.60 * scoreRisque + 0.40 * scoreSilence) * (poidsEconomique / 100.0);
            m.setScoreRisque(round(risquePondere));
            if (scoreSilence >= 100.0) {
                m.setRisqueUrgence(RisqueUrgence.URGENT);
            } else if (scoreSilence > 70.0 || risquePondere >= 50.0) {
                m.setRisqueUrgence(RisqueUrgence.ELEVE);
            } else if (risquePondere >= 25.0) {
                m.setRisqueUrgence(RisqueUrgence.MOYEN);
            } else {
                m.setRisqueUrgence(RisqueUrgence.FAIBLE);
            }

            // D. Score de valeur global
            double scoreValeur = (0.40 * potentielSur100) + (0.40 * performance) + (0.20 * poidsEconomique);
            scoreValeur = Math.round(scoreValeur * 100.0) / 100.0; // Arrondi à 2 décimales

            // E. Détermination du segment (via règles de la table controle ou fallback A/B/C/D)
            String segment;
            segment = controleService.determinerEtatParScore(TypeControle.SEGEMENTS, scoreValeur);
            if (segment == null) {
                if (scoreValeur >= 75.0) segment = "A";
                else if (scoreValeur >= 60.0) segment = "B";
                else if (scoreValeur >= 45.0) segment = "C";
                else segment = "D";
            }

            m.setScoreValeur(scoreValeur);
            m.setSegment(segment);
        }

        medecinRepository.saveAll(medecins);
        log.info("Recalcul de la segmentation Anapath terminé pour {} médecins.", totalMedecins);
    }

    private Map<Long, Double> aggregateByMedecin(List<Object[]> rows) {
        Map<Long, Double> values = new HashMap<>();
        for (Object[] row : rows) {
            values.put((Long) row[0], ((Number) row[1]).doubleValue());
        }
        return values;
    }

    private double valueFor(Map<YearMonth, Map<Long, Double>> values, YearMonth mois, Long medecinId) {
        return values.getOrDefault(mois, Map.of()).getOrDefault(medecinId, 0.0);
    }

    private double averageForPreviousMonths(Map<YearMonth, Map<Long, Double>> values, Medecin medecin, YearMonth mois) {
        double total = 0.0;
        for (int offset = 1; offset <= 3; offset++) {
            total += valueFor(values, mois.minusMonths(offset), medecin.getId());
        }
        return total / 3.0;
    }

    private double percentageDifference(double current, double reference, double minimumBase) {
        return ((current - reference) / Math.max(reference, minimumBase)) * 100.0;
    }

    private int calculateIntervalleEffectif(List<LocalDate> dates) {
        if (dates.size() < 2) return 30;
        int count = Math.min(dates.size() - 1, 3);
        long total = 0;
        for (int i = 0; i < count; i++) {
            total += ChronoUnit.DAYS.between(dates.get(i + 1), dates.get(i));
        }
        return Math.max(1, (int) Math.round(total / (double) count));
    }

    private int calculateDaysSinceLastActivity(List<LocalDate> dates) {
        return dates.isEmpty() ? 0 : Math.max(0, (int) ChronoUnit.DAYS.between(dates.get(0), LocalDate.now()));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
