package com.vactis.dto.menu;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SousMenuDto {
    private Long idMenu;
    private String label;
    private String icon;
    private String route;
}