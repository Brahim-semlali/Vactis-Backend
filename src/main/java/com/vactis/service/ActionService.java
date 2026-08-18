package com.vactis.service;

import com.vactis.dto.action.ActionFilterOptionsResponse;
import com.vactis.dto.action.ActionKpiResponse;
import com.vactis.dto.action.ActionMetaResponse;
import com.vactis.dto.action.ActionPageResponse;
import com.vactis.model.action.Action;
import com.vactis.model.action.EtatAction;
import com.vactis.model.action.UrgenceAction;
import com.vactis.model.medecin.StatutPilotage;
import com.vactis.repository.ActionRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vactis.model.Controle.TypeControle;
import java.util.List;

// Service métier pour la gestion, la recherche et le calcul des indicateurs des actions de pilotage
@Service
public class ActionService {
    private final ActionRepository actionRepository;
    private final ControleService controleService;
    private final MedecinService medecinService;

    public ActionService(
            ActionRepository actionRepository,
            ControleService controleService,
            @Lazy MedecinService medecinService
    ) {
        this.actionRepository = actionRepository;
        this.controleService = controleService;
        this.medecinService = medecinService;
    }

    // Retourne toutes les actions en base
    public List<Action> findAll() {
        return actionRepository.findAll();
    }

    // Recherche une action par son identifiant
    public Action findById(Long id) {
        return actionRepository.findById(id).orElse(null);
    }

    // Recherche les actions selon plusieurs critères de filtrage
    public List<Action> searchActions(
            String search,
            String statut,
            String segment,
            String action,
            UrgenceAction urgence,
            EtatAction etatAction,
            Boolean backlog,
            String commercial,
            String lieuOrganisme
    ) {
        return actionRepository.searchActions(
                normalize(search),
                normalize(statut),
                normalize(segment),
                normalize(action),
                urgence,
                etatAction,
                backlog,
                normalize(commercial),
                normalize(lieuOrganisme)
        );
    }

    // Retourne les options de filtres distinctes (actions, commerciaux, lieux, statuts, segments)
    public ActionFilterOptionsResponse getFilterOptions() {
        ActionFilterOptionsResponse filters = new ActionFilterOptionsResponse();
        filters.setActions(actionRepository.findDistinctActions());
        filters.setCommerciaux(actionRepository.findDistinctCommerciaux());
        filters.setLieuxOrganismes(actionRepository.findDistinctLieuxOrganismes());
        filters.setStatuts(controleService.getEtatsActifs(TypeControle.STATUT));
        filters.setSegments(controleService.getEtatsActifs(TypeControle.SEGEMENTS));
        return filters;
    }

    // Calcule les KPIs des actions (total, planifiées, réalisées, backlog, urgence silence)
    public ActionKpiResponse getKpis() {
        ActionKpiResponse kpis = new ActionKpiResponse();
        kpis.setActionsGenerees(actionRepository.countAllActions());
        kpis.setPlanifiees(actionRepository.countByEtatAction(EtatAction.PLANIFIEE));
        kpis.setVisites(actionRepository.countByEtatAction(EtatAction.REALISEE));
        kpis.setBacklog(actionRepository.countByBacklogTrue());
        kpis.setUrgenceSilence(actionRepository.countByUrgenceSilenceTrue());
        return kpis;
    }

    // Compte les actions à l'état PLANIFIEE
    public Long countPlanifiees() {
        return actionRepository.countByEtatAction(EtatAction.PLANIFIEE);
    }

    // Construit la réponse complète de la page des actions (liste, KPIs, méta, filtres)
    public ActionPageResponse getActionPage(
            String search,
            String statut,
            String segment,
            String action,
            UrgenceAction urgence,
            EtatAction etatAction,
            Boolean backlog,
            String commercial,
            String lieuOrganisme
    ) {
        medecinService.recalculerStatutsEtSegmentsDynamiques();
        syncActionsWithMedecins();

        List<Action> items = searchActions(
                search,
                statut,
                segment,
                action,
                urgence,
                etatAction,
                backlog,
                commercial,
                lieuOrganisme
        );

        ActionMetaResponse meta = new ActionMetaResponse();
        meta.setAffiches((long) items.size());
        meta.setCharges(actionRepository.countAllActions());

        ActionPageResponse response = new ActionPageResponse();
        response.setItems(items);
        response.setKpis(getKpis());
        response.setMeta(meta);
        response.setFilters(getFilterOptions());
        return response;
    }

    @Transactional
    public void syncActionsWithMedecins() {
        List<Action> actions = actionRepository.findAll();
        boolean modifie = false;
        for (Action a : actions) {
            if (a.getMedecin() != null) {
                String statutMed = a.getMedecin().getStatut();
                if (statutMed != null && !statutMed.equalsIgnoreCase(a.getStatut())) {
                    String upperStatut = statutMed.toUpperCase();
                    a.setStatut(upperStatut);
                    
                    if ("SURVEILLANCE".equals(upperStatut) || "SILENCE_CRITIQUE".equals(upperStatut) || "RETENTION".equals(upperStatut)) {
                        a.setActionRecommandee("visite urgence silence");
                        a.setUrgence(UrgenceAction.SILENCE_CRITIQUE);
                        a.setUrgenceSilence(true);
                    } else if ("PROGRESSION".equals(upperStatut)) {
                        a.setActionRecommandee("visite suivi progression");
                        a.setUrgence(UrgenceAction.FAIBLE);
                        a.setUrgenceSilence(false);
                    } else if ("ONBOARDING".equals(upperStatut)) {
                        a.setActionRecommandee("visite onboarding");
                        a.setUrgence(UrgenceAction.ELEVE);
                        a.setUrgenceSilence(false);
                    }
                    modifie = true;
                }
                if (a.getMedecin().getSegment() != null && !a.getMedecin().getSegment().equalsIgnoreCase(a.getSegment())) {
                    a.setSegment(a.getMedecin().getSegment().toUpperCase());
                    modifie = true;
                }
            }
        }
        if (modifie) {
            actionRepository.saveAll(actions);
        }
    }

    // Nettoie et normalise une chaîne (trim + null si vide)
    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
