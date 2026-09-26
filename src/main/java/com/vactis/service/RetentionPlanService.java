package com.vactis.service;

import com.vactis.model.action.Action;
import com.vactis.model.action.EtatAction;
import com.vactis.model.action.StatutPlanRetention;
import com.vactis.model.action.TypeEtapeRetention;
import com.vactis.model.action.UrgenceAction;
import com.vactis.model.medecin.Medecin;
import com.vactis.repository.ActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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

            // Plan de rétention Action 1 réalisée
            if (a.getTypeEtapeRetention() == TypeEtapeRetention.ACTION_1
                    && a.getEtatAction() == EtatAction.REALISEE
                    && a.getStatutPlanRetention() == StatutPlanRetention.EN_COURS) {

                if ("CONGE_ABSENCE".equalsIgnoreCase(a.getQualification())
                        || "RECLAMATION".equalsIgnoreCase(a.getQualification())) {
                    a.setStatutPlanRetention(StatutPlanRetention.EN_OBSERVATION);
                    a.setCommentaire("Plan suspendu en attente de la fin du congé ou du traitement de la réclamation.");
                    continue;
                }

                String statutActuel = m.getStatut() != null ? m.getStatut().toUpperCase() : "ACTIF_STABLE";

                if ("PROGRESSION".equals(statutActuel) || "ACTIF_STABLE".equals(statutActuel)) {
                    // Reprise d'activité confirmée : Succès du plan
                    a.setStatutPlanRetention(StatutPlanRetention.CLOTURE);
                    log.info("Plan de rétention pour le médecin {} clôturé avec succès.", m.getNom());
                } else if ("RETENTION".equals(statutActuel) || "INACTIF".equals(statutActuel)) {
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

                String statutActuel = m.getStatut() != null ? m.getStatut().toUpperCase() : "ACTIF_STABLE";
                if ("PROGRESSION".equals(statutActuel) || "ACTIF_STABLE".equals(statutActuel)) {
                    a.setStatutPlanRetention(StatutPlanRetention.CLOTURE);
                } else {
                    a.setStatutPlanRetention(StatutPlanRetention.ECHEC);
                }
            }
        }

        if (!nouvellesActions.isEmpty()) {
            actionRepository.saveAll(nouvellesActions);
        }
        actionRepository.saveAll(actions);
        return nouvellesActions;
    }
}
