package com.vactis.service;

import com.vactis.dto.ActionFilterOptionsResponse;
import com.vactis.dto.ActionKpiResponse;
import com.vactis.dto.ActionMetaResponse;
import com.vactis.dto.ActionPageResponse;
import com.vactis.model.Action;
import com.vactis.model.EtatAction;
import com.vactis.model.SegmentMedecin;
import com.vactis.model.StatutPilotage;
import com.vactis.model.UrgenceAction;
import com.vactis.repository.ActionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ActionService {
    private final ActionRepository actionRepository;

    public List<Action> findAll() {
        return actionRepository.findAll();
    }

    public Action findById(Long id) {
        return actionRepository.findById(id).orElse(null);
    }

    public List<Action> searchActions(
            String search,
            StatutPilotage statut,
            SegmentMedecin segment,
            String action,
            UrgenceAction urgence,
            EtatAction etatAction,
            Boolean backlog,
            String commercial,
            String lieuOrganisme
    ) {
        return actionRepository.searchActions(
                normalize(search),
                statut,
                segment,
                normalize(action),
                urgence,
                etatAction,
                backlog,
                normalize(commercial),
                normalize(lieuOrganisme)
        );
    }

    public ActionFilterOptionsResponse getFilterOptions() {
        ActionFilterOptionsResponse filters = new ActionFilterOptionsResponse();
        filters.setActions(actionRepository.findDistinctActions());
        filters.setCommerciaux(actionRepository.findDistinctCommerciaux());
        filters.setLieuxOrganismes(actionRepository.findDistinctLieuxOrganismes());
        return filters;
    }

    public ActionKpiResponse getKpis() {
        ActionKpiResponse kpis = new ActionKpiResponse();
        kpis.setActionsGenerees(actionRepository.countAllActions());
        kpis.setPlanifiees(actionRepository.countByEtatAction(EtatAction.PLANIFIEE));
        kpis.setVisites(actionRepository.countByEtatAction(EtatAction.REALISEE));
        kpis.setBacklog(actionRepository.countByBacklogTrue());
        kpis.setUrgenceSilence(actionRepository.countByUrgenceSilenceTrue());
        return kpis;
    }

    public Long countPlanifiees() {
        return actionRepository.countByEtatAction(EtatAction.PLANIFIEE);
    }

    public ActionPageResponse getActionPage(
            String search,
            StatutPilotage statut,
            SegmentMedecin segment,
            String action,
            UrgenceAction urgence,
            EtatAction etatAction,
            Boolean backlog,
            String commercial,
            String lieuOrganisme
    ) {
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

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
