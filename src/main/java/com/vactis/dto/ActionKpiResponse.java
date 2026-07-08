package com.vactis.dto;

import lombok.Data;

@Data
public class ActionKpiResponse {
    private Long actionsGenerees;
    private Long planifiees;
    private Long visites;
    private Long backlog;
    private Long urgenceSilence;
}
