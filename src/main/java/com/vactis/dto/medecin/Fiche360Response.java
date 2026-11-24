package com.vactis.dto.medecin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Fiche médecin 360° : toute l'histoire du laboratoire avec un médecin sur une seule vue
 * (identité, activité mensuelle, frise chronologique, visites, actions, réclamations, dossiers).
 * Sert aussi de source unique à l'export PDF.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Fiche360Response {

    private Identite identite;
    private Indicateurs indicateurs;
    /** 12 derniers mois disponibles, du plus ancien au plus récent. */
    private List<Mois> mois;
    /** Frise chronologique, du plus récent au plus ancien. */
    private List<Evenement> evenements;
    private List<Visite> visites;
    private List<ActionLigne> actions;
    private List<ReclamationLigne> reclamations;
    /** Dossiers reçus par le laboratoire sur les 12 derniers mois (sans référence patient). */
    private List<Dossier> dossiers;
    /** Obstacles principaux relevés en visite, du plus fréquent au moins fréquent. */
    private Map<String, Long> obstacles;
    private List<CommercialLigne> commerciaux;
    private LocalDate dateEdition;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Identite {
        private Long medecinId;
        private String codeMedecin;
        private String nomComplet;
        private String specialite;
        private String organisme;
        private String ville;
        private String telephone;
        private String email;
        private String segment;
        private String statut;
        private Double notePotentiel;
        private String commercialReferent;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Indicateurs {
        private long ca12Mois;
        private long dossiers12Mois;
        private double caMoyenMensuel;
        private LocalDate dernierDossier;
        private Integer joursSansActivite;
        private long visites;
        private long visitesRealisees;
        private LocalDate derniereVisite;
        private long actions;
        private long actionsOuvertes;
        private long reclamations;
        private long reclamationsOuvertes;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Mois {
        private String mois;
        private long ca;
        private long dossiers;
        private String statut;
        private int visites;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Evenement {
        private LocalDate date;
        /** VISITE, ACTION, RECLAMATION, STATUT, ALERTE. */
        private String type;
        private String titre;
        private String detail;
        private String commercial;
        /** POSITIF, NEGATIF ou NEUTRE. */
        private String tonalite;
        private Long referenceId;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Visite {
        private Long id;
        private LocalDate date;
        private String commercial;
        private String commercialAppui;
        private boolean vactis;
        private String typeVisite;
        private String statutVisite;
        private String qualification;
        private String obstaclePrincipal;
        private Double note;
        private String commentaire;
        private String motifNonRealisation;
        private String prochaineAction;
        private LocalDate dateProchaineAction;
        private boolean reclamation;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ActionLigne {
        private Long id;
        private String cycle;
        private String actionRecommandee;
        private String urgence;
        private String etat;
        private String commercial;
        private LocalDate dateEcheance;
        private boolean visitee;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ReclamationLigne {
        private Long id;
        private LocalDate dateDeclaration;
        private String categorie;
        private String priorite;
        private String statut;
        private boolean ouverte;
        private String commercial;
        private String responsable;
        private String description;
        private String solution;
        private LocalDate dateResolution;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Dossier {
        private LocalDate dateReception;
        private String reference;
        private String typeAnalyse;
        private Integer nombreAnalyses;
        private String statut;
        private Integer montant;
        private boolean urgent;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CommercialLigne {
        private String commercial;
        private long visites;
        private long appuis;
        private LocalDate derniereVisite;
    }
}
