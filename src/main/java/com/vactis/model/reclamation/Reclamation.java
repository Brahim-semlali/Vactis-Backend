package com.vactis.model.reclamation;

import com.vactis.model.medecin.Medecin;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entité opérationnelle de gestion et traçabilité des réclamations (Section 7).
 */
@Entity
@Table(name = "reclamations")
@Data
@NoArgsConstructor
public class Reclamation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "medecin_id", nullable = false)
    private Medecin medecin;

    @Column(name = "commercial_declarant", nullable = false, length = 255)
    private String commercialDeclarant;

    @Column(name = "date_declaration", nullable = false)
    private LocalDate dateDeclaration = LocalDate.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CategorieReclamation categorie = CategorieReclamation.AUTRE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PrioriteReclamation priorite = PrioriteReclamation.MOYENNE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutReclamation statut = StatutReclamation.OUVERTE;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "responsable_traitement", length = 255)
    private String responsableTraitement;

    @Column(name = "solution_apportee", columnDefinition = "TEXT")
    private String solutionApportee;

    @Column(name = "date_resolution")
    private LocalDate dateResolution;

    @Column(name = "retour_terrain_id")
    private Long retourTerrainId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
