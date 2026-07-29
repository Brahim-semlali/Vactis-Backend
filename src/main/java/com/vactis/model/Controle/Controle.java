package com.vactis.model.Controle;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Table(name = "controle")
@NoArgsConstructor
public class Controle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idControle ;

    @Enumerated(EnumType.STRING)
    private TypeControle type ;

    private String nom_etat ;
    private Long minCA ;
    private Long maxCA ;
    private Boolean Active = true ;

}
