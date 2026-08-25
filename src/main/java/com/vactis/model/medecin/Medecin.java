package com.vactis.model.medecin;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "medecins")
@Data
@NoArgsConstructor
public class Medecin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code_medecin", nullable = false, unique = true, length = 20)
    private String codeMedecin;

    @Column(nullable = false, length = 255)
    private String nom;

    @Column(nullable = false, length = 255)
    private String prenom;

    @Column(nullable = false, length = 255)
    private String specialite;

    @Column(nullable = false, length = 255)
    private String organisme;

    @Column(length = 100)
    private String ville;

    @Column(length = 20)
    private String telephone;

    @Column(length = 255)
    private String email;

    @Column(nullable = false, length = 20)
    private String statut = "NOUVEAU";

    @Column(length = 30)
    private String segment;

    @Column(name = "note_input")
    private Double noteInput;

    @Transient
    private String sourcePotentiel;

    @Column(name = "score_valeur")
    private Double scoreValeur;

    private Double potentielSur100;

    private Double performanceSur100;

    @Transient
    private Integer rangPerformance;

    @Transient
    private Integer totalPortefeuillePerformance;

    private Double caMensuelMoyen;

    private Double poidsEcoSur100;

    @Transient
    private Double caNormaliseSur100;

    @Transient
    private Double volumeNormaliseSur100;

    @Transient
    private Double maxCaPortefeuille;

    @Transient
    private Double maxVolumePortefeuille;

    private Double variationMixteSur100;

    private Double referenceCa;

    private Double referenceVolume;

    private Double variationCa;

    private Double variationVolume;

    private Integer joursSansActivite;

    private Double baisseReference;

    private Double baisseCourte;

    private String fiabilite;

    private Integer intervalleEffectif;

    private Double scoreSilence;

    private Double scoreRisque;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_pilotage", nullable = false, length = 30)
    private StatutPilotage statutPilotage = StatutPilotage.ACTIF;

    @Enumerated(EnumType.STRING)
    @Column(name = "risque_urgence", nullable = false, length = 20)
    private RisqueUrgence risqueUrgence = RisqueUrgence.FAIBLE;

    @Column(name = "ca_mois")
    private Integer caMois;

    @Column(name = "ca_baseline")
    private Integer caBaseline;

    @Column(name = "ca_total")
    private Integer caTotal;

    @Column(name = "total_cas")
    private Integer totalCas;

    @Column(name = "date_premiere_collaboration")
    private LocalDate datePremiereCollaboration;

    @Column(name = "date_derniere_activite")
    private LocalDate dateDerniereActivite;

    @Column(name = "commercial_referent", length = 255)
    private String commercialReferent;

    @Column(columnDefinition = "TEXT")
    private String commentaire;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
