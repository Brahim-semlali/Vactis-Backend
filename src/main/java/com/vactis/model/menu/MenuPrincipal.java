package com.vactis.model.menu;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@Table(name = "menu_principal")
public class MenuPrincipal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idMenuPrinc;

    private String nom;
    private String icone;

    @Column(name = "menu_order")
    private int ordre;

    @OneToMany(mappedBy = "menuPrincipal", cascade = CascadeType.ALL)
    @OrderBy("order ASC")
    @JsonIgnore
    private List<MenuItem> sousMenus = new ArrayList<>();
}