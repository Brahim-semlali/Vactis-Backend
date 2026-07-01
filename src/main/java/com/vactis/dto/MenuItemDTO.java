package com.vactis.dto;

import lombok.Data;

@Data
public class MenuItemDTO {
    private Long idMenu;
    private String label;
    private String icon;
    private String route;
    private int order;
}
