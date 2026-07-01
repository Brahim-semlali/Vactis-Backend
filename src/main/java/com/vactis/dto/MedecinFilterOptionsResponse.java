package com.vactis.dto;

import lombok.Data;

import java.util.List;

@Data
public class MedecinFilterOptionsResponse {
    private List<String> specialites;
    private List<String> organismes;
}
