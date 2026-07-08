package com.vactis.dto;

import lombok.Data;

import java.util.List;

@Data
public class ActionFilterOptionsResponse {
    private List<String> actions;
    private List<String> commerciaux;
    private List<String> lieuxOrganismes;
}
