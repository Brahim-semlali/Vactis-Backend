package com.vactis.service;

import com.vactis.model.action.Action;
import com.vactis.model.action.EtatAction;
import com.vactis.model.action.StatutPlanRetention;
import com.vactis.model.action.TypeEtapeRetention;
import com.vactis.model.action.UrgenceAction;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.reclamation.Reclamation;
import com.vactis.model.reclamation.StatutReclamation;
import com.vactis.repository.ActionRepository;
import com.vactis.repository.ReclamationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Service de pilotage du protocole de rétention à deux actions (Section 3).
 * Assure le suivi séquentiel : Action 1 -> Évaluation cycle suivant -> Action 2 ou Clôture.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RetentionPlanService {

    private final ActionRepository actionRepository;
    private final ReclamationRepository reclamationRepository;

    @Scheduled(cron = "0 0 5 1 * ?")
    public void evaluerCycleMensuelCloture() {
        evaluerPlansRetention(YearMonth.now().minusMonths(1).toString());
    }

    /**
     * Traite les plans de rétention actifs lors de la clôture mensuelle ou du recalcul.
     */
    @Transactional
    public List<Action> evaluerPlansRetention(String cycleCourant) {
        List<Action> actions = actionRepository.findAll();
        List<Action> nouvellesActions = new ArrayList<>();

        for (Action a : actions) {
            Medecin m = a.getMedecin();
            if (m == null) continue;

            if (a.getTypeEtapeRetention() == TypeEtapeRetention.ACTION_1
                    && a.getStatutPlanRetention() == StatutPlanRetention.EN_OBSERVATION
                    && reprendrePlanSuspendu(a, cycleCourant)) {
                continue;
            }

            // Plan de rétention Action 1 réalisée
            if (a.getTypeEtapeRetention() == TypeEtapeRetention.ACTION_1
                    && a.getEtatAction() == EtatAction.REALISEE
                    && a.getStatutPlanRetention() == StatutPlanRetention.EN_COURS) {
                if (!Boolean.TRUE.equals(a.getSuspensionLevee())
                    && ("CONGE_ABSENCE".equalsIgnoreCase(a.getQualification())
                    || "RECLAMATION".equalsIgnoreCase(a.getQualification()))) {
                    a.setStatutPlanRetention(StatutPlanRetention.EN_OBSERVATION);
                    a.setCommentaire("Plan suspendu en attente de la fin du congé ou du traitement de la réclamation.");
                    continue;
                }

                if (!cycleCompletApres(a, cycleCourant)) continue;

                String statutActuel = m.getStatut() != null ? m.getStatut().toUpperCase() : "ACTIF_STABLE";

                if (statutAmeliore(statutActuel)) {
                    // Reprise d'activité confirmée : Succès du plan
                    a.setStatutPlanRetention(StatutPlanRetention.CLOTURE);
                    log.info("Plan de rétention pour le médecin {} clôturé avec succès.", m.getNom());
                } else if ("RETENTION".equals(statutActuel)) {
                    // Risque persistant : Déclenchement de l'Action 2 (Escalade de sauvetage)
                    a.setStatutPlanRetention(StatutPlanRetention.EN_OBSERVATION);

                    Action action2 = new Action();
                    action2.setMedecin(m);
                    action2.setStatut(statutActuel);
                    action2.setSegment(m.getSegment());
                    action2.setActionParenteId(a.getId());
                    action2.setTypeEtapeRetention(TypeEtapeRetention.ACTION_2);
                    action2.setStatutPlanRetention(StatutPlanRetention.EN_COURS);
                    action2.setActionRecommandee("visite retention niveau 2 (direction / biologiste)");
                    action2.setUrgence(UrgenceAction.ELEVE);
                    action2.setEtatAction(EtatAction.PLANIFIEE);
                    action2.setDateVisite(LocalDate.now().plusDays(15));
                    action2.setCommercial(a.getCommercial());
                    action2.setLieuOrganisme(a.getLieuOrganisme());
                    action2.setBacklog(false);
                    action2.setUrgenceSilence(false);
                    action2.setCycleMensuel(cycleCourant);
                    action2.setHorizonJours(15);
                    action2.setDateEcheance(LocalDate.now().plusDays(15));
                    action2.setCommentaire("Action 2 de rétention déclenchée suite à l'échec de relance de l'Action 1.");

                    nouvellesActions.add(action2);
                    log.warn("Action 2 de rétention générée pour le médecin {}.", m.getNom());
                }
            } else if (a.getTypeEtapeRetention() == TypeEtapeRetention.ACTION_2
                    && a.getEtatAction() == EtatAction.REALISEE
                    && a.getStatutPlanRetention() == StatutPlanRetention.EN_COURS) {

                if (!cycleCompletApres(a, cycleCourant)) continue;

                String statutActuel = m.getStatut() != null ? m.getStatut().toUpperCase() : "ACTIF_STABLE";
                if ("PROGRESSION".equals(statutActuel) || "ACTIF_STABLE".equals(statutActuel)
                    || "ACTIVITE_IRREGULIERE".equals(statutActuel) || "ACTIF".equals(statutActuel)) {
                    a.setStatutPlanRetention(StatutPlanRetention.CLOTURE);
                } else {
                    a.setStatutPlanRetention(StatutPlanRetention.ECHEC);
                    a.setProchaineAction("Arbitrage de la direction requis");
                    a.setCommentaire("Aucune amélioration après l’Action 2. Arbitrage de la direction requis.");
                }
            }
        }

        if (!nouvellesActions.isEmpty()) {
            actionRepository.saveAll(nouvellesActions);
        }
        actionRepository.saveAll(actions);
        return nouvellesActions;
    }

    private boolean cycleCompletApres(Action action, String cycleCourant) {
        if (cycleCourant == null || cycleCourant.isBlank()) return false;
        try {
            YearMonth cycleEvalue = YearMonth.parse(cycleCourant);
            String cycleAction = action.getCycleMensuel();
            if (cycleAction == null || cycleAction.isBlank()) {
                cycleAction = action.getCreatedAt() == null
                        ? null
                        : YearMonth.from(action.getCreatedAt()).toString();
            }
            return cycleAction != null && cycleEvalue.isAfter(YearMonth.parse(cycleAction));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean reprendrePlanSuspendu(Action action, String cycleCourant) {
        YearMonth cycleEvaluation;
        try {
            cycleEvaluation = YearMonth.parse(cycleCourant);
        } catch (RuntimeException exception) {
            return false;
        }

        LocalDate dateReprise;
        if ("CONGE_ABSENCE".equalsIgnoreCase(action.getQualification())) {
            dateReprise = action.getDateRetourPrevue() != null ? action.getDateRetourPrevue() : action.getDateRevue();
            if (dateReprise == null || dateReprise.isAfter(LocalDate.now())) return false;
        } else if ("RECLAMATION".equalsIgnoreCase(action.getQualification())) {
            if (action.getReclamationId() == null) return false;
            Reclamation reclamation = reclamationRepository.findById(action.getReclamationId()).orElse(null);
            if (reclamation == null || !reclamationTraitee(reclamation.getStatut())) return false;
            dateReprise = reclamation.getDateResolution() != null ? reclamation.getDateResolution() : LocalDate.now();
        } else {
            return false;
        }

        YearMonth cycleReprise = YearMonth.from(dateReprise);
        if (!cycleEvaluation.isAfter(cycleReprise)) return false;
        action.setSuspensionLevee(true);
        action.setStatutPlanRetention(StatutPlanRetention.EN_COURS);
        action.setCycleMensuel(cycleReprise.toString());
        return true;
    }

    private boolean reclamationTraitee(StatutReclamation statut) {
        return statut == StatutReclamation.TRAITEE
                || statut == StatutReclamation.CLOTURE_SANS_ACTION
                || statut == StatutReclamation.RESOLUE
                || statut == StatutReclamation.REJETEE;
    }

    private boolean statutAmeliore(String statut) {
        return "PROGRESSION".equals(statut)
                || "ACTIF_STABLE".equals(statut)
                || "ACTIF".equals(statut)
                || "SURVEILLANCE".equals(statut)
                || "ACTIVITE_IRREGULIERE".equals(statut);
    }
}
