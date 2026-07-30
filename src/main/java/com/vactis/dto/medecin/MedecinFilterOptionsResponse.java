package com.vactis.dto.medecin;


import lombok.Data;

import java.util.List;

@Data
public class MedecinFilterOptionsResponse {
    private List<String> specialites;
    private List<String> organismes;
    private List<String> statuts;
    private List<String> segments;
}
