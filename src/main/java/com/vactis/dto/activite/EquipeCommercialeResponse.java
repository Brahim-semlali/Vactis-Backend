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
 * - visite commune : passage réalisé avec un commercial en appui. Elle compte une fois pour l'équipe et
 *   comme participation commune pour chacun des deux, sans priorité liée à l'ordre des noms ;
 * - « seul » : passage réalisé sans commercial en appui (base des taux individuels) ;
 * - évolution observée : règle R4 (statut M → M+1), uniquement pour les visites VACTIS ;
 * - date incohérente : date de visite absente, future ou antérieure à 2000, écartée de toute la lecture.
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
    private RapportBaisses rapportBaisses;
    /** Passages écartés pour date incohérente (absente, future ou antérieure à 2000). */
    private List<DateEcartee> datesEcartees;
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
        /** Passages réalisés avec un commercial en appui, comptés une seule fois. */
        private long visitesCommunes;
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

        // ── Lecture « seul / commune » : aucune priorité entre les participants d'une visite commune ──
        /** Passages réalisés seul (sans appui). */
        private long visitesSeul;
        /** Passages réalisés à deux, que le commercial soit saisi comme visiteur ou en appui. */
        private long visitesCommunes;
        private long vactisSeul;
        private long horsVactisSeul;
        private long servicesSeul;
        private long echangesSeul;
        private long medecinsEnEchangeSeul;
        private long prospectsSeul;
        private long retoursQualifiesSeul;
        private long retoursFavorablesSeul;
        private long refusSeul;
        private long progressionsSeul;
        private long stablesSeul;
        private long baissesSeul;
        private long comparablesSeul;
        private long echangesCommuns;
        private long retoursFavorablesCommuns;
    }

    /** Lecture équilibrée des baisses de statut observées après les visites VACTIS. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RapportBaisses {
        private long baisses;
        /** Transitions comparables : progression, stabilité ou baisse. */
        private long comparables;
        private long medecinsEnBaisse;
        /** Médecins dont une baisse est observée sur au moins deux mois de visite différents. */
        private long medecinsBaissesRepetees;
        /** Retours qualifiés favorables alors que le statut du médecin a baissé. */
        private long favorablesMalgreBaisse;
        private List<Transition> transitionsFrequentes;
        private List<LigneEvolution> progressions;
        private List<LigneEvolution> baissesObservees;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Transition {
        private String statutAvant;
        private String statutApres;
        private long nombre;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LigneEvolution {
        private Long retourTerrainId;
        private Long medecinId;
        private String medecinNom;
        private LocalDate dateVisite;
        private String statutAvant;
        private String statutApres;
        private String commercial;
        private String commercialAppui;
        private String qualification;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DateEcartee {
        private Long retourTerrainId;
        private String medecinNom;
        private String commercial;
        private LocalDate dateVisite;
        private String motif;
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
        /** Échanges qualifiés défavorables, à reprendre. */
        private long retoursDefavorables;
        private long baissesObservees;
        /** Visites qualifiées favorables suivies d'une baisse du statut : à vérifier avec les données du laboratoire. */
        private long baissesMalgreFavorable;
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
