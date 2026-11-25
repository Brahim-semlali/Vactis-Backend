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
import com.vactis.service.PaliersControle;
import com.vactis.service.RetourTerrainService;
import com.vactis.service.ActionHorizonPolicy;
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
        // Utilise la fin du dernier mois avec des données en base,
        // et non la date du jour, pour ne pas créer un mois courant à zéro
        // quand les données de ce mois sont absentes (règle R : la grille
        // s'arrête au dernier mois qui contient des données).
        recalculerSegmentationPortefeuille(derniereReferenceData());
    }

    /** Retourne le dernier jour du mois le plus récent qui contient des données
     *  d'extraction. Repli sur LocalDate.now() si la base est vide. */
    private LocalDate derniereReferenceData() {
        List<LocalDate> dates = extractionDonneesRepository.findAllDatesDescending();
        if (dates.isEmpty()) {
            log.warn("Aucune donnée d'extraction en base — dateReference = LocalDate.now().");
            return LocalDate.now();
        }
        return YearMonth.from(dates.get(0)).atEndOfMonth();
    }

    @Transactional
    public void recalculerSegmentationPortefeuille(LocalDate dateReference) {
        List<Medecin> medecins = medecinRepository.findAllWithImportedDossiers().stream()
            .filter(m -> !medecinRepository.isPseudoMedecin(m)).toList();
        if (medecins.isEmpty()) return;

        int totalMedecins = medecins.size();
        // Paliers dynamiques (table Contrôle), chargés une seule fois pour tout le portefeuille
        PaliersControle paliersFiabilite = PaliersControle.ou(controleService.paliers(TypeControle.FIABILITE), TypeControle.FIABILITE);
        PaliersControle paliersUrgence = PaliersControle.ou(controleService.paliers(TypeControle.URGENCE), TypeControle.URGENCE);

        YearMonth moisCourant = YearMonth.from(dateReference);
        List<LocalDate> allDatesDescending = extractionDonneesRepository.findAllDatesDescending();
        YearMonth premierMoisGrille = allDatesDescending.isEmpty()
            ? moisCourant : YearMonth.from(allDatesDescending.get(allDatesDescending.size() - 1));
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
        // CA total et cas totaux sur tout l'historique (pour le poids économique et le CA moyen R12)
        Map<Long, Double> caTotalParMedecin = aggregateByMedecin(
            extractionDonneesRepository.sumCaGroupedByMedecin());
        Map<Long, Double> casTotauxParMedecin = new HashMap<>();
        for (Object[] row : extractionDonneesRepository.countCasGroupedByMedecin()) {
            casTotauxParMedecin.put(((Number) row[0]).longValue(), ((Number) row[1]).doubleValue());
        }

        // R12 : Nombre de mois de la grille commune (janvier 2025 à moisCourant = 21 mois)
        LocalDate dateDebutGrille = allDatesDescending.isEmpty() ? dateReference : allDatesDescending.get(allDatesDescending.size() - 1);
        long nbMoisGrille = Math.max(1, ChronoUnit.MONTHS.between(YearMonth.from(dateDebutGrille), moisCourant) + 1);

        // CA mensuel moyen selon R12 : CA historique total / nombre de mois de la grille (21)
        for (Medecin medecin : medecins) {
            double caTotal = caTotalParMedecin.getOrDefault(medecin.getId(), 0.0);
            double caMoyen = caTotal / (double) nbMoisGrille;
            caMoyenParMedecin.put(medecin.getId(), caMoyen);
        }

        // 1. Nombre de cas par médecin (mois courant) — pour le volume
        Map<Long, Double> volumeCourantParMedecin = volumeParMois.get(moisCourant);
        Map<Long, Long> casMap = new HashMap<>();
        for (Map.Entry<Long, Double> entry : volumeCourantParMedecin.entrySet()) {
            casMap.put(entry.getKey(), entry.getValue().longValue());
        }

        // Maxima pour la normalisation du poids économique (tout l'historique)
        double maxCa = medecins.stream()
            .mapToDouble(m -> caTotalParMedecin.getOrDefault(m.getId(), 0.0))
            .max()
            .orElse(0.0);
        if (maxCa <= 0) maxCa = 1.0;

        double maxCas = medecins.stream()
            .mapToDouble(m -> casTotauxParMedecin.getOrDefault(m.getId(), 0.0))
                .max()
                .orElse(0.0);
        if (maxCas <= 0) maxCas = 1.0;

        // 4. Trier par CA total historique croissant pour déterminer le rang (aligné sur l'encadrant)
        List<Medecin> medecinsTriesParCa = new ArrayList<>(medecins);
        medecinsTriesParCa.sort(ordrePerformanceParCa(caTotalParMedecin));

        Map<Long, Double> performanceMap = new HashMap<>();
        List<Double> intensitesCalculees = new ArrayList<>();
        Map<Long, Long> moisActifsParMedecin = new HashMap<>();
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
            double potentielSur100 = ScoreMetierPolicy.potentiel(noteSur5);
            m.setPotentielSur100(potentielSur100);

            double caCourant = valueFor(caParMois, moisCourant, m.getId());
            double volumeCourant = valueFor(volumeParMois, moisCourant, m.getId());
            double caReference = averageForPreviousMonths(caParMois, m, moisCourant, premierMoisGrille);
            double volumeReference = averageForPreviousMonths(volumeParMois, m, moisCourant, premierMoisGrille);
                int nombreMoisReference = (int) Math.min(3L,
                    Math.max(0L, ChronoUnit.MONTHS.between(premierMoisGrille, moisCourant)));
            double variationCa = IndicateursMensuelsPolicy.variation(caCourant, caReference, 300.0);
            double variationVolume = IndicateursMensuelsPolicy.variation(volumeCourant, volumeReference, 1.0);
            double variationMixte = StatutMensuelPolicy.variationMixte(variationCa, variationVolume);
            m.setCaMoisAnalyse(caCourant);
            m.setCasMoisAnalyse(volumeCourant);
            m.setDateReferenceIndicateurs(dateReference);
            m.setNombreMoisReference(nombreMoisReference);
            m.setReferenceCa(caReference);
            m.setReferenceVolume(volumeReference);
            m.setVariationCa(variationCa);
            m.setVariationVolume(variationVolume);
            m.setVariationMixteSur100(variationMixte);

            // B. Performance (40%) : Rang percentile CA dans le portefeuille
            double performance = performanceMap.getOrDefault(m.getId(), 0.0);
            m.setCaMensuelMoyen(round(caMoyenParMedecin.getOrDefault(m.getId(), 0.0)));
            m.setPerformanceSur100(performance);

            // C. Poids économique (20%) : 50% CA normalisé + 50% Cas normalisé
            double caPhys = caTotalParMedecin.getOrDefault(m.getId(), 0.0);
            double casPhys = casTotauxParMedecin.getOrDefault(m.getId(), 0.0);

            double caNormalise = (caPhys / maxCa) * 100.0;
            double casNormalise = (casPhys / maxCas) * 100.0;
            double poidsEconomique = ScoreMetierPolicy.poidsEconomique(caNormalise, casNormalise);
            m.setCaNormaliseSur100(round(caNormalise));
            m.setVolumeNormaliseSur100(round(casNormalise));
            m.setMaxCaPortefeuille(round(maxCa));
            m.setMaxVolumePortefeuille(round(maxCas));
            m.setPoidsEcoSur100(poidsEconomique);

            // D. Score de valeur global
            double scoreValeur = ScoreMetierPolicy.scoreValeur(potentielSur100, performance, poidsEconomique);
            scoreValeur = Math.round(scoreValeur * 100.0) / 100.0; // Arrondi à 2 décimales

            // E. Détermination du segment (via règles de la table controle ou fallback A/B/C/D)
            String segment = controleService.determinerEtatParScore(TypeControle.SEGEMENTS, scoreValeur);
            if (segment == null) {
                segment = ScoreMetierPolicy.segment(scoreValeur);
            }
            m.setScoreValeur(scoreValeur);
            m.setSegment(segment);

            List<LocalDate> datesMedecin = datesJusquaReference(
                extractionDonneesRepository.findDatesReceptionByMedecinId(m.getId()), dateReference);
            if (datesMedecin.isEmpty()) {
                m.setCaMoisAnalyse(null);
                m.setCasMoisAnalyse(null);
                m.setReferenceCa(null);
                m.setReferenceVolume(null);
                m.setVariationCa(null);
                m.setVariationVolume(null);
                m.setVariationMixteSur100(null);
            }
            IndicateursMensuelsPolicy.SilenceMetrics silence = IndicateursMensuelsPolicy.silence(datesMedecin, dateReference);
            double intervalleEffectifPrecise = silence.intervalleEffectif();
            m.setIntervalleEffectif(intervalleEffectifPrecise);
            m.setDateDerniereActiviteCalcul(silence.dateDerniereActivite());

            long moisActifs = datesMedecin.stream()
                .map(YearMonth::from)
                .distinct()
                .count();
            moisActifsParMedecin.put(m.getId(), moisActifs);
            boolean ancienneteSuffisante = m.getDatePremiereCollaboration() != null
                && !m.getDatePremiereCollaboration().plusMonths(6).isAfter(dateReference);
            m.setFiabilite(fiabilite(paliersFiabilite, moisActifs, ancienneteSuffisante));

            LocalDate derniereActivite = datesMedecin.isEmpty() ? null : datesMedecin.get(0);
            Optional<RetourTerrain> conge = retourTerrainService.getHistorique(m).stream()
                .filter(r -> r.getQualification() == com.vactis.model.medecin.QualificationVisite.CONGE_ABSENCE && r.getDateDepart() != null)
                .findFirst();
            int joursSansActivite = conge.isPresent()
                ? CongeSilencePolicy.calculerJoursSansActiviteAjustes(
                    derniereActivite, dateReference, conge.get().getDateDepart(), conge.get().getDateRetourPrevue(), conge.get().getDateRevue())
                : silence.joursSansActivite();
            m.setJoursSansActivite(datesMedecin.isEmpty() ? null : joursSansActivite);

            double scoreSilence = conge.isPresent()
                    ? Math.min(100.0, (joursSansActivite / intervalleEffectifPrecise) * 20.0)
                    : silence.scoreSilence();
            m.setScoreSilence(scoreSilence);
            m.setSourceRythme(silence.sourceRythme());
            m.setClasseSilence(conge.isPresent()
                    ? IndicateursMensuelsPolicy.classeSilence(joursSansActivite / intervalleEffectifPrecise)
                    : silence.classeSilence());

            // R9 : Baisses sur variation mixte (référence et courte)
                double varCaCourte = IndicateursMensuelsPolicy.variation(caCourant,
                    valueFor(caParMois, moisCourant.minusMonths(1), m.getId()), 300.0);
                double varVolumeCourte = IndicateursMensuelsPolicy.variation(volumeCourant,
                    valueFor(volumeParMois, moisCourant.minusMonths(1), m.getId()), 1.0);
            double varMixteCourte = StatutMensuelPolicy.variationMixte(varCaCourte, varVolumeCourte);

            double baisseReference = Math.max(0.0, -variationMixte);
            double baisseCourte = Math.max(0.0, -varMixteCourte);
            m.setBaisseReference(round(baisseReference));
            m.setBaisseCourte(round(baisseCourte));

            // Tendance Risque (60% baisse courte + 40% baisse référence)
            double tendanceRisque = Math.min(100.0, (0.40 * baisseReference) + (0.60 * baisseCourte));
            m.setTendanceRisque(round(tendanceRisque));

            // Score Risque R9 : (60% tendance + 40% silence) × (0,70 × scoreValeur/100 + 0,30)
            double scoreRisque = ScoreMetierPolicy.scoreRisque(tendanceRisque, scoreSilence, scoreValeur);
            m.setScoreRisque(round(scoreRisque));

            // Intensité : 60% valeur + 40% risque
            double intensiteArrondie = ScoreMetierPolicy.intensiteArrondie(scoreValeur, scoreRisque);
            m.setIntensiteRisque(intensiteArrondie);
            intensitesCalculees.add(intensiteArrondie);
        }

        double medianeIntensite = ActionHorizonPolicy.percentile(intensitesCalculees, 0.50);
        double p80Intensite = ActionHorizonPolicy.percentile(intensitesCalculees, 0.80);

        // Seuil d'impact R11 : médiane du top 20% des CA moyens des médecins retenus
        List<Double> caMoyensEligibles = medecins.stream()
            .filter(m -> !"INACTIF".equalsIgnoreCase(m.getStatut())
                      && !"EXCLU".equalsIgnoreCase(m.getStatut())
                  && !"ONBOARDING".equalsIgnoreCase(m.getStatut())
                  && !Boolean.TRUE.equals(m.getIsProfilIrregulier())
                  && moisActifsParMedecin.getOrDefault(m.getId(), 0L) >= 2)
            .map(Medecin::getCaMensuelMoyen)
            .filter(Objects::nonNull)
            .filter(ca -> ca > 0.0)
            .toList();
        List<Double> caMoyensPositifs = medecins.stream()
            .map(Medecin::getCaMensuelMoyen)
            .filter(Objects::nonNull)
            .filter(ca -> ca > 0.0)
            .toList();
        double seuilImpact = IndicateursMensuelsPolicy.seuilImpact(caMoyensEligibles, caMoyensPositifs);

        for (Medecin m : medecins) {
            double intensite = m.getIntensiteRisque() == null ? 0.0 : m.getIntensiteRisque();
            m.setNiveauIntensite(ScoreMetierPolicy.niveauIntensite(intensite, medianeIntensite, p80Intensite));

            // Urgence R11 : brute puis facteur de matérialité
            double intScore = ScoreMetierPolicy.pointsIntensite(intensite, medianeIntensite, p80Intensite);
            double phaseScore = 100.0; // Action 1
            double delaiScore = 0.0;
            double scoreRisque = m.getScoreRisque() != null ? m.getScoreRisque() : 0.0;
            double urgenceBrute = ScoreMetierPolicy.scoreUrgence(scoreRisque, phaseScore, intScore, delaiScore);

            double caCourant = valueFor(caParMois, moisCourant, m.getId());
            double perteCa = Math.max(0.0, (m.getReferenceCa() != null ? m.getReferenceCa() : 0.0) - caCourant);
            double facteur = ScoreMetierPolicy.facteurMaterialite(perteCa, seuilImpact);

            double urgenceFinale = ("INACTIF".equalsIgnoreCase(m.getStatut())
                    || "EXCLU".equalsIgnoreCase(m.getStatut())
                    || Boolean.TRUE.equals(m.getIsProfilIrregulier())
                    || "BLOQUE".equalsIgnoreCase(m.getStatut()))
                ? 0.0
                : ScoreMetierPolicy.urgenceFinale(urgenceBrute, facteur);
            m.setUrgenceBrute(urgenceBrute);
            m.setPerteCa(perteCa);
            m.setSeuilImpact(seuilImpact);
            m.setFacteurMaterialite(facteur);
            m.setScoreUrgence(urgenceFinale);

            double scoreSilence = m.getScoreSilence() != null ? m.getScoreSilence() : 0.0;
            m.setRisqueUrgence(niveauUrgence(paliersUrgence, urgenceFinale, scoreRisque, scoreSilence));
        }

            List<Medecin> medecinsAvecActionClassement = medecins.stream()
                .filter(m -> m.getScoreUrgence() != null && m.getScoreUrgence() > 0.0)
                .sorted(Comparator.comparing(Medecin::getScoreUrgence, Comparator.reverseOrder())
                    .thenComparing(Medecin::getScoreRisque, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(Medecin::getPerteCa, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
            for (Medecin medecin : medecins) medecin.setRangUrgence(null);
            for (int index = 0; index < medecinsAvecActionClassement.size(); index++) {
                medecinsAvecActionClassement.get(index).setRangUrgence(index + 1);
            }

        medecinRepository.saveAll(medecins);
        log.info("Recalcul de la segmentation Anapath terminé pour {} médecins.", totalMedecins);
    }

    private double scoreValeurDisponible(double potentiel, double performance, double poidsEconomique) {
        return (0.40 * potentiel) + (0.40 * performance) + (0.20 * poidsEconomique);
    }

    static Comparator<Medecin> ordrePerformanceParCa(Map<Long, Double> caMoyenParMedecin) {
        return Comparator.comparingDouble((Medecin medecin) ->
                        caMoyenParMedecin.getOrDefault(medecin.getId(), 0.0))
                .thenComparing(Medecin::getId);
    }

    /**
     * Fiabilité selon les paliers FIABILITE (mois actifs). FIABLE exige en plus 6 mois
     * d'ancienneté de collaboration ; sinon le médecin est rétrogradé en PARTIEL.
     */
    static String fiabilite(PaliersControle paliers, long moisActifs, boolean ancienneteSuffisante) {
        String etat = paliers.etatPour(moisActifs);
        if ("FIABLE".equals(etat) && !ancienneteSuffisante) return "PARTIEL";
        return etat != null ? etat : "NON_FIABLE";
    }

    /**
     * Niveau d'urgence : paliers URGENCE appliqués au score d'urgence finale, et au score
     * de risque plafonné à ELEVE. Le silence garde ses escalades fixes (>70 ELEVE, >=100 URGENT).
     */
    static RisqueUrgence niveauUrgence(PaliersControle paliers, double urgenceFinale, double scoreRisque, double scoreSilence) {
        RisqueUrgence niveau = versRisque(paliers.etatPour(urgenceFinale));
        niveau = plusHaut(niveau, plusBas(versRisque(paliers.etatPour(scoreRisque)), RisqueUrgence.ELEVE));
        if (scoreSilence >= 100.0) niveau = plusHaut(niveau, RisqueUrgence.URGENT);
        else if (scoreSilence > 70.0) niveau = plusHaut(niveau, RisqueUrgence.ELEVE);
        return niveau;
    }

    private static RisqueUrgence versRisque(String etat) {
        if (etat == null) return RisqueUrgence.FAIBLE;
        try {
            return RisqueUrgence.valueOf(etat);
        } catch (IllegalArgumentException e) {
            return RisqueUrgence.FAIBLE;
        }
    }

    private static RisqueUrgence plusHaut(RisqueUrgence a, RisqueUrgence b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    private static RisqueUrgence plusBas(RisqueUrgence a, RisqueUrgence b) {
        return a.ordinal() <= b.ordinal() ? a : b;
    }

    private double phaseUrgence(String statut) {
        return switch (statut) {
            case "RETENTION" -> 100.0;
            case "SURVEILLANCE" -> 70.0;
            case "ONBOARDING" -> 55.0;
            case "PROGRESSION" -> 35.0;
            default -> 45.0;
        };
    }

    private String statutPourUrgence(Medecin medecin, double variationMixte) {
        if (medecin.getStatut() != null) return medecin.getStatut().toUpperCase();
        if (variationMixte > 20.0) return "PROGRESSION";
        if (variationMixte < -40.0) return "RETENTION";
        if (variationMixte < -10.0) return "SURVEILLANCE";
        return "ACTIF";
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

    private double averageForPreviousMonths(Map<YearMonth, Map<Long, Double>> values, Medecin medecin,
                                           YearMonth mois, YearMonth premierMoisGrille) {
        List<Double> moisDisponibles = new ArrayList<>();
        for (int offset = 1; offset <= 3; offset++) {
            YearMonth moisReference = mois.minusMonths(offset);
            if (!moisReference.isBefore(premierMoisGrille)) {
                moisDisponibles.add(valueFor(values, moisReference, medecin.getId()));
            }
        }
        return IndicateursMensuelsPolicy.moyenneReference(moisDisponibles);
    }

    private double percentageDifference(double current, double reference, double minimumBase) {
        return ((current - reference) / Math.max(reference, minimumBase)) * 100.0;
    }

    static List<LocalDate> datesJusquaReference(List<LocalDate> dates, LocalDate dateReference) {
        return dates.stream().filter(date -> !date.isAfter(dateReference)).toList();
    }

    static int calculateIntervalleEffectif(List<LocalDate> dates) {
        if (dates.size() < 2) return 30;
        int count = Math.min(dates.size() - 1, 3);
        long total = 0;
        for (int i = 0; i < count; i++) {
            total += ChronoUnit.DAYS.between(dates.get(i + 1), dates.get(i));
        }
        return Math.max(1, (int) Math.round(total / (double) count));
    }

    static double calculateIntervalleEffectifPrecise(List<LocalDate> dates, LocalDate dateReference) {
        return IndicateursMensuelsPolicy.silence(dates, dateReference).intervalleEffectif();
    }

    static int calculateDaysSinceLastActivity(List<LocalDate> dates, LocalDate dateReference) {
        return IndicateursMensuelsPolicy.silence(dates, dateReference).joursSansActivite();
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
