package com.vactis.controller.action;

import com.vactis.dto.action.ActionPageResponse;
import com.vactis.model.action.Action;
import com.vactis.model.action.EtatAction;
import com.vactis.model.action.UrgenceAction;
import com.vactis.model.medecin.SegmentMedecin;
import com.vactis.model.medecin.StatutPilotage;
import com.vactis.service.action.ActionService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/actions")
public class ActionController {
    private final ActionService actionService;

    @GetMapping
    public ActionPageResponse getActions(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) StatutPilotage statut,
            @RequestParam(required = false) SegmentMedecin segment,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UrgenceAction urgence,
            @RequestParam(required = false) EtatAction etatAction,
            @RequestParam(required = false) Boolean backlog,
            @RequestParam(required = false) String commercial,
            @RequestParam(required = false) String lieuOrganisme
    ) {
        return actionService.getActionPage(
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
    }

    @GetMapping("/{id}")
    public Action getActionById(@PathVariable Long id) {
        return actionService.findById(id);
    }
}
