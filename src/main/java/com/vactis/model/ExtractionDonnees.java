package com.vactis.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "data_fictif")
@Data
@NoArgsConstructor
public class ExtractionDonnees {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "date_reception", nullable = false)
    private LocalDate dateReception;

    @Column(nullable = false, length = 255)
    private String medecin;

    @Column(nullable = false, length = 255)
    private String specialite;

    @Column(nullable = false, length = 255)
    private String organisme;

    @Column(name = "lieu_prelevement", nullable = false, length = 255)
    private String lieuPrelevement;

    @Column(name = "prix_total", nullable = false)
    private Integer prixTotal;

    @Column(name = "prix_a_payer", nullable = false)
    private Integer prixAPayer;

    @Column(nullable = false)
    private boolean urgent;
}
