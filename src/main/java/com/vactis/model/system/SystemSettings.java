package com.vactis.model.system;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vactis.model.auth.Users;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_settings")
@Data
@NoArgsConstructor
public class SystemSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "duree_session_minutes")
    private Integer dureeSessionMinutes = 60;

    @Column(name = "duree_inactivite_jours")
    private Integer dureeInactiviteJours = 90;

    @Column(name = "mdp_longueur_minimale")
    private Integer mdpLongueurMinimale = 8;

    @Column(name = "mdp_exige_majuscules")
    private Boolean mdpExigeMajuscule = false;

    @Column(name = "mdp_exige_chiffre")
    private Boolean mdpExigeChiffre = false;

    @Column(name = "mdp_exige_caractere_special")
    private Boolean mdpExigeCaractereSpecial = false;

    @Column(name = "mdp_expiration_jours")
    private Integer mdpExpirationJours = 0;

    @Column(name = "max_tentatives_connexion")
    private Integer maxTentativesConnexion = 5;

    @Column(name = "duree_blocage_minutes")
    private Integer dureeBlocageMinutes = 15;

    @Column(name = "journal_connexion_actif")
    private Boolean journalConnexionActif = true;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_id")
    private Users updatedBy;

    @PrePersist
    @PreUpdate
    void validate() {
        if (dureeSessionMinutes == null || dureeSessionMinutes <= 0 || dureeInactiviteJours == null || dureeInactiviteJours <= 0
                || mdpLongueurMinimale == null || mdpLongueurMinimale <= 0 || mdpExpirationJours == null || mdpExpirationJours < 0
                || maxTentativesConnexion == null || maxTentativesConnexion <= 0 || dureeBlocageMinutes == null || dureeBlocageMinutes < 0
                || mdpExigeMajuscule == null || mdpExigeChiffre == null || mdpExigeCaractereSpecial == null || journalConnexionActif == null) {
            throw new IllegalArgumentException("Les paramètres doivent respecter des valeurs positives; les expirations et blocages peuvent être à zéro");
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }
}