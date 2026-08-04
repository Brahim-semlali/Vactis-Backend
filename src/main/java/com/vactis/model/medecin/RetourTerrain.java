package com.vactis.model.medecin;

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

    @Column(nullable = false)
    private Double note; // Note sur 5 (ex: 4.0)

    @Column(name = "date_visite", nullable = false)
    private LocalDate dateVisite;

    @Column(columnDefinition = "TEXT")
    private String commentaire;

    @Column(length = 255)
    private String visiteur;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
