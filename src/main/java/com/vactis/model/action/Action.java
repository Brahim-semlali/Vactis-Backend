package com.vactis.model.action;

import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.StatutPilotage;

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
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "actions")
@Data
@NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Action {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "medecin_id", nullable = false)
    private Medecin medecin;


    @Column(nullable = false, length = 30)
    private String statut;


    @Column(length = 30)
    private String segment;

    @Column(name = "action_recommandee", nullable = false, length = 255)
    private String actionRecommandee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UrgenceAction urgence;

    @Enumerated(EnumType.STRING)
    @Column(name = "etat_action", nullable = false, length = 20)
    private EtatAction etatAction = EtatAction.PLANIFIEE;

    @Column(name = "date_visite")
    private LocalDate dateVisite;

    @Column(nullable = false, length = 255)
    private String commercial;

    @Column(name = "lieu_organisme", nullable = false, length = 255)
    private String lieuOrganisme;

    @Column(nullable = false)
    private Boolean backlog = false;

    @Column(name = "urgence_silence", nullable = false)
    private Boolean urgenceSilence = false;

    @Column(name = "cycle_mensuel", length = 20)
    private String cycleMensuel;

    @Column(columnDefinition = "TEXT")
    private String commentaire;

    @Column(name = "reserved_by", length = 255)
    private String reservedBy;

    @Column(name = "reserved_at")
    private LocalDateTime reservedAt;

    @Column(name = "is_reserved")
    private Boolean isReserved = false;

    @Column(name = "motif_non_realisation", columnDefinition = "TEXT")
    private String motifNonRealisation;

    @Column(name = "qualification", length = 50)
    private String qualification;

    @Column(name = "prochaine_action", length = 255)
    private String prochaineAction;

    @Column(name = "date_prochaine_action")
    private LocalDate dateProchaineAction;

    @Transient
    private Double derniereNoteTerrain;

    @Transient
    private Integer joursSansActivite;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
