package com.vactis.model.alerte;

import com.vactis.model.medecin.Medecin;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Entité représentant une alerte précoce issue du batch hebdomadaire (Section 10).
 */
@Entity
@Table(name = "alertes_hebdomadaires")
@Getter
@Setter
@NoArgsConstructor
public class AlerteHebdomadaire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "medecin_id", nullable = false)
    private Medecin medecin;

    @Column(name = "date_detection", nullable = false)
    private LocalDate dateDetection;

    @Column(name = "jours_silence")
    private Integer joursSilence;

    @Column(name = "intervalle_moyen")
    private Double intervalleMoyen;

    @Column(name = "score_urgence_silence")
    private Double scoreUrgenceSilence;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_alerte", length = 30)
    private TypeAlerte typeAlerte = TypeAlerte.RUPTURE_RYTHME;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_alerte", length = 30)
    private StatutAlerte statutAlerte = StatutAlerte.A_TRAITER;

    @Column(name = "action_generee_id")
    private Long actionGenereeId;

    @Column(name = "message", length = 500)
    private String message;
}
