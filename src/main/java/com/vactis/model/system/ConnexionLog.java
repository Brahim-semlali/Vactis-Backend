package com.vactis.model.system;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "connexion_logs")
@Data
@NoArgsConstructor
public class ConnexionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "date_connexion", nullable = false)
    private LocalDateTime dateConnexion;

    @Column(name = "date_deconnexion")
    private LocalDateTime dateDeconnexion;

    @Column(name = "adresse_ip")
    private String adresseIp;

    @Column(nullable = false)
    private Boolean succes;
}