package com.vactis.dto.suiviequipe;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/** Réponses de l'API Suivi équipe (historique par commercial et évolution des médecins visités). */
public final class SuiviEquipeDtos {

    private SuiviEquipeDtos() {
    }

    /** Synthèse d'un commercial sur la période filtrée. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommercialResume {
        private String commercial;
        private long actionsPrevues;
        private long actionsEnRetard;
        private long visitesRealisees;
        private long visitesNonRealisees;
        private long visitesVactis;
        private long visitesLibres;
        private long qualificationsFavorables;
        private long qualificationsDefavorables;
        private long qualificationsNeutres;
        private long qualificationsNonRenseignees;
        private long medecinsVisites;
        private long reclamationsDeclarees;
        private long reclamationsOuvertes;
        private LocalDate derniereVisite;
    }

    /** Une visite de l'historique (VACTIS ou libre). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VisiteLigne {
        private Long id;
        private LocalDate dateVisite;
        private String commercial;
        private Long medecinId;
        private String medecinNom;
        private String typeVisite;
        /** true = visite issue d'une action VACTIS, false = visite libre. */
        private boolean vactis;
        private String statutVisite;
        private String qualification;
        private String commentaire;
        private String motifNonRealisation;
        private boolean reclamation;
        private String prochaineAction;
        private LocalDate dateProchaineAction;
        /** Une autre visite réalisée du même médecin a eu lieu dans les 30 jours. */
        private boolean visiteRepetee;
    }

    /** Une réclamation avec son responsable et son échéance. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReclamationLigne {
        private Long id;
        private LocalDate dateDeclaration;
        private LocalDate dateVisite;
        private Long medecinId;
        private String medecinNom;
        private String commercial;
        private String categorie;
        private String priorite;
        private String statut;
        private boolean ouverte;
        private String responsable;
        private LocalDate dateEcheance;
        private boolean enRetard;
        private String description;
        private String prochaineInstruction;
    }

    /** Chronologie complète d'un médecin : visites, activité mensuelle et comparaisons avant/après. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvolutionMedecin {
        private Long medecinId;
        private String medecinNom;
        private String specialite;
        private String segment;
        private String statutActuel;
        /** Dernier mois pour lequel des données d'activité existent dans la base. */
        private String dernierMoisDisponible;
        private List<VisiteLigne> visites;
        private List<MoisActivite> mois;
        private List<ComparaisonVisite> comparaisons;
        private String avertissement;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MoisActivite {
        private String mois;
        private long ca;
        private long cas;
        private String statut;
        private int visites;
    }

    /** Comparaison observée autour d'une visite réalisée (pas une preuve de causalité). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComparaisonVisite {
        private Long visiteId;
        private LocalDate dateVisite;
        private String commercial;
        private String qualification;
        /** Moyenne mensuelle sur les 3 mois précédant le mois de la visite (mois disponibles). */
        private Double caMoyenAvant;
        private Double casMoyenAvant;
        private int moisAvantDisponibles;
        /** Moyenne mensuelle sur les 3 mois suivant le mois de la visite (mois disponibles). */
        private Double caMoyenApres;
        private Double casMoyenApres;
        private int moisApresDisponibles;
        private Double evolutionCaPct;
        /** Repères ponctuels : null si le mois n'est pas encore disponible. */
        private Long caMPlus1;
        private Long caMPlus3;
        private String statutAvant;
        private String statutApres;
        /** EN_HAUSSE, STABLE, EN_BAISSE ou NON_OBSERVABLE. */
        private String tendance;
        private boolean donneesInsuffisantes;
        /** D'autres visites ont eu lieu dans la fenêtre d'observation : effet non isolable. */
        private boolean visitesRepetees;
    }
}
