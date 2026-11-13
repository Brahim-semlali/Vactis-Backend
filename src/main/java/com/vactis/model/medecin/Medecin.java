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
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

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

    @JsonProperty("referenceCas")
    public Double getReferenceCas() {
        return referenceVolume;
    }

    @JsonProperty("variationCas")
    public Double getVariationCas() {
        return variationVolume;
    }
    private Double variationCa;

    private Double variationVolume;

    private Integer joursSansActivite;

    private Double baisseReference;

    private Double baisseCourte;

    private String fiabilite;

    private Double intervalleEffectif;

    private Double scoreSilence;

    private String sourceRythme;

    private String classeSilence;

    private Double caMoisAnalyse;

    private Double casMoisAnalyse;

    private LocalDate dateReferenceIndicateurs;

    private LocalDate dateDerniereActiviteCalcul;

    private Integer nombreMoisReference;

    private Double scoreRisque;

    @Column(name = "is_a_reactiver_manuel", columnDefinition = "boolean default false")
    private Boolean isAReactiverManuel = false;

    @Column(name = "is_profil_irregulier", columnDefinition = "boolean default false")
    private Boolean isProfilIrregulier = false;

    @Column(name = "score_urgence")
    private Double scoreUrgence;

    private Double urgenceBrute;

    private Double perteCa;

    private Double seuilImpact;

    private Double facteurMaterialite;

    private Integer rangUrgence;

    @Column(name = "intensite_risque")
    private Double intensiteRisque;

    @Column(name = "niveau_intensite", length = 20)
    private String niveauIntensite;

    @Column(name = "tendance_risque")
    private Double tendanceRisque;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_pilotage", nullable = false, columnDefinition = "varchar(30)")
    private StatutPilotage statutPilotage = StatutPilotage.ACTIF_STABLE;

    @Enumerated(EnumType.STRING)
    @Column(name = "risque_urgence", nullable = false, columnDefinition = "varchar(20)")
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
