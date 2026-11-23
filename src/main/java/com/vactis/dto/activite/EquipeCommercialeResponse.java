package com.vactis.dto.activite;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Lecture activité → onglet « Équipe commerciale ».
 *
 * Définitions :
 * - passage réalisé : visite saisie dont le statut n'est pas NON_REALISEE ;
 * - visite VACTIS : passage lié à une action VACTIS ; hors VACTIS : sans action ;
 * - passage de service : passage hors VACTIS de type SERVICE, exclu des échanges commerciaux ;
 * - échange commercial : passage réalisé qui n'est pas un passage de service ;
 * - prospect : médecin sans aucun dossier dans l'extraction du laboratoire ;
 * - relais : visite VACTIS réalisée par un autre commercial que le prioritaire de l'action ;
 * - évolution observée : règle R4 (statut M → M+1), uniquement pour les visites VACTIS.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipeCommercialeResponse {

    private LocalDate debut;
    private LocalDate fin;
    /** Commercial filtré, null pour toute l'équipe. */
    private String commercial;
    /** Mois (yyyy-MM) ayant au moins une visite saisie, du plus récent au plus ancien. */
    private List<String> moisDisponibles;
    /** Commerciaux connus : visiteurs, appuis et attributaires du classeur actif. */
    private List<String> commerciaux;

    private Synthese synthese;
    private List<LigneCommercial> parCommercial;
    /** Répartition des qualifications des échanges commerciaux (services exclus). */
    private Map<String, Long> qualifications;
    /** Évolutions observées après les visites VACTIS : FAVORABLE, STABLE, DEFAVORABLE, NON_OBSERVABLE. */
    private Map<String, Long> evolutions;
    private List<LigneVisite> visites;
    private Decisions decisions;
    private String avertissement;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Synthese {
        private long passagesSaisis;
        private long passagesRealises;
        private double tauxRealisation;
        private long visitesVactis;
        private long horsVactis;
        private long passagesService;
        private long echangesCommerciaux;
        private long medecinsEnEchange;
        private long prospects;
        private long relais;
        private long participationsAppui;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LigneCommercial {
        private String commercial;
        private long passagesSaisis;
        /** Passages réalisés où le commercial est le visiteur principal. */
        private long passagesPrincipaux;
        private long participationsAppui;
        private long visitesVactis;
        private long horsVactis;
        private long services;
        private long echanges;
        private long medecinsEnEchange;
        private long prospects;
        private long retoursQualifies;
        private long retoursFavorables;
        private long refusOuReclamations;
        private long progressions;
        /** Visites VACTIS dont l'évolution est observable. */
        private long comparables;
        private long baisses;
        private long relais;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LigneVisite {
        private Long retourTerrainId;
        private LocalDate dateVisite;
        private Long medecinId;
        private String medecinNom;
        private String commercial;
        private String commercialAppui;
        /** Commercial prioritaire de l'action VACTIS (null hors VACTIS). */
        private String commercialPrioritaire;
        private boolean relais;
        private boolean vactis;
        private boolean service;
        private boolean prospect;
        private boolean realisee;
        private String typeVisite;
        private String actionRecommandee;
        private String qualification;
        private boolean reclamation;
        private String statutAvant;
        private String statutApres;
        /** null hors VACTIS ou pour un passage non réalisé. */
        private String evolution;
        private String commentaire;
        private String motifNonRealisation;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Decisions {
        /** Cycle mensuel du classeur actif (dernier cycle d'actions). */
        private String cycleActif;
        private List<ActionSansVisite> actionsSansVisite;
        private long refusOuReclamations;
        private long baissesObservees;
        private long retoursACompleter;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ActionSansVisite {
        private Long actionId;
        private Long medecinId;
        private String medecinNom;
        private String actionRecommandee;
        private String commercial;
        private String urgence;
        private LocalDate dateEcheance;
    }
}
