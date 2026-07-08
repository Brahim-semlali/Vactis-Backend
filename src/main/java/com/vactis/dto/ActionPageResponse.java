package com.vactis.dto;

import com.vactis.model.Action;
import lombok.Data;

import java.util.List;

@Data
public class ActionPageResponse {
    private List<Action> items;
    private ActionKpiResponse kpis;
    private ActionMetaResponse meta;
    private ActionFilterOptionsResponse filters;
}
