package com.vactis.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Table(name = "menu_items")
@NoArgsConstructor
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idMenu;

    private String label;
    private String icon;
    private String route;

    @Column(name = "menu_order")
    private int order;

    private Boolean isVisible = true;

    @Transient
    private List<Long> allowedUserIds = new ArrayList<>();

    @PrePersist
    @PreUpdate
    void setDefaultVisibility() {
        if (isVisible == null) {
            isVisible = true;
        }
    }
}
