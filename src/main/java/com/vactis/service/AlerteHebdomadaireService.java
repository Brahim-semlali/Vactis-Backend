package com.vactis.service;

import com.vactis.model.Controle.TypeControle;
import com.vactis.model.action.Action;
import com.vactis.model.action.EtatAction;
import com.vactis.model.action.UrgenceAction;
import com.vactis.model.alerte.AlerteHebdomadaire;
import com.vactis.model.alerte.StatutAlerte;
import com.vactis.model.alerte.TypeAlerte;
import com.vactis.model.medecin.Medecin;
import com.vactis.repository.ActionRepository;
import com.vactis.repository.AlerteHebdomadaireRepository;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.RetourTerrainRepository;
import com.vactis.model.medecin.QualificationVisite;
import com.vactis.model.medecin.RetourTerrain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service de surveillance précoce et batch hebdomadaire VACTIS (Section 10).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlerteHebdomadaireService {

    private final AlerteHebdomadaireRepository alerteHebdomadaireRepository;
    private final MedecinRepository medecinRepository;
    private final ExtractionDonneesRepository extractionDonneesRepository;
    private final ActionRepository actionRepository;
    private final RetourTerrainRepository retourTerrainRepository;
    private final ControleService controleService;

    /**
     * Tâche hebdomadaire programmée : tourne chaque lundi à 04:00 du matin.
     */
    @Scheduled(cron = "0 0 4 ? * MON")
    public void cronSurveillanceHebdomadaire() {
        log.info("Lancement automatique du batch hebdomadaire de détection de rupture de rythme...");
        executerBatchHebdomadaire();
    }

    /**
     * Exécute le calcul complet de détection des ruptures de rythme (Section 10).
     */
    @Transactional
    public List<AlerteHebdomadaire> executerBatchHebdomadaire() {
        return executerBatchHebdomadaire(LocalDate.now());
        }

    @Transactional
    public List<AlerteHebdomadaire> refreshImportedDataset(LocalDate dateReference) {
        alerteHebdomadaireRepository.deleteByStatutAlerte(StatutAlerte.A_TRAITER);
        return executerBatchHebdomadaire(dateReference);
    }

        @Transactional
    public List<AlerteHebdomadaire> executerBatchHebdomadaire(LocalDate dateEvaluation) {
        List<Medecin> medecins = medecinRepository.findAllWithImportedDossiers();
        List<AlerteHebdomadaire> alertesCreees = new ArrayList<>();
        // Paliers de rupture (table Contrôle) chargés une seule fois pour tout le batch
        PaliersControle paliersRupture = controleService != null
                ? controleService.paliers(TypeControle.RUPTURE)
                : PaliersControle.defauts(TypeControle.RUPTURE);

        for (Medecin m : medecins) {
            String statut = m.getStatut() != null ? m.getStatut().toUpperCase() : "INACTIF";
            String segment = m.getSegment() != null ? m.getSegment().toUpperCase() : "D";
            boolean fiabiliteSuffisante = "FIABLE".equalsIgnoreCase(m.getFiabilite()) || "PARTIEL".equalsIgnoreCase(m.getFiabilite());
            boolean impactSuffisant = (m.getScoreRisque() != null && m.getScoreRisque() >= 25.0)
                    || (m.getIntensiteRisque() != null && m.getIntensiteRisque() >= 25.0);
            if (absenceEnCours(m, dateEvaluation)) {
                continue;
            }
            if (!Set.of("ACTIF", "ACTIF_STABLE", "PROGRESSION", "SURVEILLANCE", "RETENTION", "ACTIVITE_IRREGULIERE").contains(statut)) {
                continue;
            }

            List<LocalDate> dates = extractionDonneesRepository.findDatesReceptionByMedecinId(m.getId());
            if (dates == null || dates.isEmpty()) continue;

            Long totalCas = extractionDonneesRepository.countCasByMedecinId(m.getId());
            if (totalCas == null) totalCas = 0L;

            Set<YearMonth> moisActifs = dates.stream()
                    .map(YearMonth::from)
                    .collect(Collectors.toSet());
            LocalDate dateDerniere = dates.get(0);
            boolean rythmeRecentFiable = SilenceAlertPolicy.rythmeRecentFiable(totalCas, moisActifs.size(), dates.size());
            double intervalleMoyen = calculerIntervalleAttendu(dates, rythmeRecentFiable);

            long joursSilence = ChronoUnit.DAYS.between(dateDerniere, dateEvaluation);
            if (joursSilence < 0) joursSilence = 0;

            // Paliers RUPTURE de la table Contrôle (défaut : <150 % aucune ; 150-200 % surveillance ; >=200 % critique).
            double ratio = intervalleMoyen > 0 ? joursSilence / intervalleMoyen : 0.0;

            SilenceAlertPolicy.NiveauRupture niveauRupture = SilenceAlertPolicy.niveauRupture(ratio, paliersRupture);
            if (niveauRupture != SilenceAlertPolicy.NiveauRupture.AUCUNE) {
                TypeAlerte type = TypeAlerte.RUPTURE_RYTHME;
                if (niveauRupture == SilenceAlertPolicy.NiveauRupture.CRITIQUE) {
                    type = TypeAlerte.CHURN_IMMINENT;
                } else if (joursSilence >= 30) {
                    type = TypeAlerte.SILENCE_PROLONGE;
                }

                // Score urgence silence = 40% silence + 30% risque + 30% valeur.
                double risqueScore = m.getScoreRisque() != null ? m.getScoreRisque() : 0.0;
                double valeurScore = m.getScoreValeur() != null ? m.getScoreValeur() : 0.0;
                double score = SilenceAlertPolicy.scoreUrgence(ratio, risqueScore, valeurScore);

                if (!SilenceAlertPolicy.alerteStrategiqueEligible(
                    statut, segment, m.getNoteInput(), fiabiliteSuffisante, impactSuffisant,
                    niveauRupture, Boolean.TRUE.equals(m.getIsProfilIrregulier()))) {
                    continue;
                }

                // Vérifier si une alerte active existe déjà dans les 7 derniers jours
                Optional<AlerteHebdomadaire> existante = alerteHebdomadaireRepository.findRecentActiveAlerte(
                    m, dateEvaluation.minusDays(7), StatutAlerte.A_TRAITER
                );

                if (existante.isEmpty()) {
                    AlerteHebdomadaire alerte = new AlerteHebdomadaire();
                    alerte.setMedecin(m);
                    alerte.setDateDetection(dateEvaluation);
                    alerte.setJoursSilence((int) joursSilence);
                    alerte.setIntervalleMoyen(Math.round(intervalleMoyen * 10.0) / 10.0);
                    alerte.setScoreUrgenceSilence(score);
                    alerte.setTypeAlerte(type);
                    alerte.setStatutAlerte(StatutAlerte.A_TRAITER);
                    alerte.setMessage(String.format(
                            "Rupture de rythme : %d jours sans dépôt (moyenne usuelle : %.1f j). Prescripteur Segment %s.",
                            joursSilence, intervalleMoyen, m.getSegment() != null ? m.getSegment() : "Standard"
                    ));
                    alertesCreees.add(alerteHebdomadaireRepository.save(alerte));
                }
            }
        }

        log.info("Batch hebdomadaire achevé avec succès. {} alertes créées.", alertesCreees.size());
        return alertesCreees;
    }

    double calculerIntervalleAttendu(List<LocalDate> dates, boolean rythmeRecentFiable) {
        if (dates == null || dates.size() < 2) return 30.0;
        List<LocalDate> ordonnees = dates.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
        if (ordonnees.size() < 2) return 30.0;

        int intervalles = rythmeRecentFiable
                ? Math.min(3, ordonnees.size() - 1)
                : ordonnees.size() - 1;
        long totalJours = 0L;
        for (int index = 0; index < intervalles; index++) {
            totalJours += ChronoUnit.DAYS.between(ordonnees.get(index + 1), ordonnees.get(index));
        }
        return Math.max(1.0, (double) totalJours / intervalles);
    }

    private boolean absenceEnCours(Medecin medecin, LocalDate now) {
        List<RetourTerrain> historiques = retourTerrainRepository.findByMedecinOrderByDateVisiteDescCreatedAtDesc(medecin);
        if (historiques == null || historiques.isEmpty()) return false;
        return historiques.stream().anyMatch(retour -> {
            if (retour.getQualification() != QualificationVisite.CONGE_ABSENCE
                || retour.getDateDepart() == null
                || retour.getDateDepart().isAfter(now)) return false;
            LocalDate dateRepriseOuRevue = retour.getDateRetourPrevue() != null
                ? retour.getDateRetourPrevue() : retour.getDateRevue();
            return dateRepriseOuRevue == null || !dateRepriseOuRevue.isBefore(now);
        });
    }

    public List<AlerteHebdomadaire> getAlertesActives() {
        return alerteHebdomadaireRepository.findByStatutAlerteOrderByScoreUrgenceSilenceDesc(StatutAlerte.A_TRAITER);
    }

    public List<AlerteHebdomadaire> getToutesAlertes() {
        return alerteHebdomadaireRepository.findAll();
    }

    @Transactional
    public AlerteHebdomadaire traiterAlerte(Long id, StatutAlerte nouveauStatut) {
        AlerteHebdomadaire alerte = alerteHebdomadaireRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alerte introuvable"));
        alerte.setStatutAlerte(nouveauStatut);
        return alerteHebdomadaireRepository.save(alerte);
    }

    @Transactional
    public Action genererActionDepuisAlerte(Long alerteId, String commercial) {
        AlerteHebdomadaire alerte = alerteHebdomadaireRepository.findById(alerteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alerte introuvable"));

        Medecin m = alerte.getMedecin();
        Action action = new Action();
        action.setMedecin(m);
        action.setCommercial(commercial != null && !commercial.isBlank() ? commercial : "Commercial");
        action.setActionRecommandee("Prise de contact suite à silence critique (" + alerte.getJoursSilence() + "j)");
        action.setCommentaire(alerte.getMessage());
        action.setStatut(m.getStatut() != null ? m.getStatut() : "SURVEILLANCE");
        action.setSegment(m.getSegment() != null ? m.getSegment() : "B");
        action.setLieuOrganisme(m.getOrganisme() != null ? m.getOrganisme() : "Cabinet médical");
        action.setEtatAction(EtatAction.PLANIFIEE);
        action.setUrgence(UrgenceAction.SILENCE_CRITIQUE);
        action.setUrgenceSilence(true);
        action.setDateEcheance(LocalDate.now().plusDays(3)); // Horizon recommandé pour un silence stratégique critique
        action.setHorizonJours(3);

        Action savedAction = actionRepository.save(action);

        alerte.setStatutAlerte(StatutAlerte.ACTION_CREEE);
        alerte.setActionGenereeId(savedAction.getId());
        alerteHebdomadaireRepository.save(alerte);

        return savedAction;
    }
}
