package com.vactis.model.menu;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vactis.model.Roles.Roles;
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

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_menu_princ")
    private MenuPrincipal menuPrincipal;

    @PrePersist
    @PreUpdate
    void setDefaultVisibility() {
        if (isVisible == null) {
            isVisible = true;
        }
    }

    @JsonIgnore
    @ManyToMany(mappedBy = "menuItems")
    private List<Roles> roles = new ArrayList<>();
}
