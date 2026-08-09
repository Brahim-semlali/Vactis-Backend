package com.vactis.model.medecin;

import com.vactis.model.action.Action;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "retours_terrain")
@Data
@NoArgsConstructor
public class RetourTerrain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medecin_id", nullable = false)
    private Medecin medecin;

    @Column(name = "nom_medecin", length = 255)
    private String nomMedecin;

    @Column(nullable = false)
    private Double note; // Note sur 5 (ex: 4.0)

    @Column(name = "date_visite", nullable = false)
    private LocalDate dateVisite;

    @Column(columnDefinition = "TEXT")
    private String commentaire;

    @Column(length = 255)
    private String visiteur;

    // --- Niveau 3 : champs d'exécution terrain ---

    /** Statut d'exécution de la visite (REALISEE par défaut pour la migration des données existantes). */
    @Enumerated(EnumType.STRING)
    @Column(name = "statut_visite", nullable = false, length = 20,
            columnDefinition = "varchar(20) default 'REALISEE'")
    private StatutVisite statutVisite = StatutVisite.REALISEE;

    /** Qualification du retour terrain (NON_RENSEIGNE par défaut pour la migration). */
    @Enumerated(EnumType.STRING)
    @Column(name = "qualification", nullable = false, length = 20,
            columnDefinition = "varchar(20) default 'NON_RENSEIGNE'")
    private QualificationVisite qualification = QualificationVisite.NON_RENSEIGNE;

    /** Indique si la visite a donné lieu à une réclamation médecin. */
    @Column(name = "reclamation", nullable = false,
            columnDefinition = "boolean default false")
    private Boolean reclamation = false;

    // --- Niveau 4 : lien action VACTIS et type de visite ---

    /**
     * Lien optionnel vers l'action VACTIS ayant déclenché cette visite.
     * NULL = visite hors VACTIS (saisie terrain libre).
     * NON NULL = visite VACTIS (issue d'une action générée).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "action_id", nullable = true)
    private Action action;

    /**
     * Type de visite saisi par le commercial (Fidélisation, Rétention, etc.).
     * NULL pour les retours historiques antérieurs au Niveau 4 → affiché comme AUTRE.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type_visite", length = 30)
    private TypeVisite typeVisite;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    @PreUpdate
    public void fillNomMedecin() {
        if (medecin != null && (nomMedecin == null || nomMedecin.isBlank())) {
            String nom = medecin.getNom() != null ? medecin.getNom() : "";
            String prenom = medecin.getPrenom() != null ? medecin.getPrenom() : "";
            this.nomMedecin = (nom + " " + prenom).trim();
        }
    }
}
