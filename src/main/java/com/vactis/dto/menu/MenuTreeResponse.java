package com.vactis.dto.menu;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class MenuTreeResponse {
    private Long idMenuPrinc;
    private String nom;
    private String icone;
    private List<SousMenuDto> sousMenus;
}