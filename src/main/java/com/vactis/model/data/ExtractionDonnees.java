package com.vactis.model.data;

import com.vactis.model.medecin.Medecin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "medecin_id", nullable = true)
    private Medecin medecin;

    @Column(name = "reference_dossier", nullable = false, unique = true, length = 50)
    private String referenceDossier;

    @Column(name = "date_reception", nullable = false)
    private LocalDate dateReception;

    @Column(name = "date_prelevement")
    private LocalDate datePrelevement;

    @Column(name = "type_analyse", nullable = false, length = 255)
    private String typeAnalyse;

    @Column(name = "nombre_analyses", nullable = false)
    private Integer nombreAnalyses = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_dossier", nullable = false, length = 30)
    private StatutDossier statutDossier = StatutDossier.RECU;

    @Column(name = "lieu_prelevement", nullable = false, length = 255)
    private String lieuPrelevement;

    @Column(name = "patient_ref", length = 30)
    private String patientRef;

    @Column(name = "prix_total", nullable = false)
    private Integer prixTotal;

    @Column(name = "montant_rembourse", nullable = false)
    private Integer montantRembourse = 0;

    @Column(name = "prix_a_payer", nullable = false)
    private Integer prixAPayer;

    @Column(nullable = false)
    private boolean urgent;
}
