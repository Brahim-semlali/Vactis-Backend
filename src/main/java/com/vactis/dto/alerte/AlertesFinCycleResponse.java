package com.vactis.dto.alerte;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Alertes de fin de cycle : ce qui risque de rester ouvert à la clôture du classeur actif.
 *
 * <p>Les alertes sont calculées à la volée à partir des données (actions, retours terrain,
 * réclamations) : elles disparaissent d'elles-mêmes dès que la situation est réglée
 * (visite saisie, retour qualifié, réclamation traitée).</p>
 *
 * <ul>
 *   <li>{@code ACTION_NON_VISITEE} : action du cycle actif, non réalisée et sans retour terrain.</li>
 *   <li>{@code RETOUR_A_QUALIFIER} : échange réalisé pendant le mois du cycle, qualification « non renseigné ».</li>
 *   <li>{@code RECLAMATION_EN_ATTENTE} : réclamation non traitée, déclarée depuis au moins 7 jours.</li>
 *   <li>{@code REALISATION_FAIBLE} (vue équipe) : commercial ayant visité moins de 60 % des actions du cycle.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertesFinCycleResponse {

    /** Classeur actif (AAAA-MM), null si aucune action n'est chargée. */
    private String cycle;
    private LocalDate dateCloture;
    /** Jours restants avant la clôture ; négatif si le cycle est déjà clos. */
    private Long joursRestants;
    /** Vrai à partir de J-7 : les alertes sont alors remontées dans la cloche. */
    private boolean fenetreActive;
    /** Vrai à partir de J-3 (ou après la clôture). */
    private boolean urgent;
    /** Vrai pour la direction / le pilotage : alertes de toute l'équipe et tableau par commercial. */
    private boolean vueEquipe;
    private int fenetreJours;
    private int seuilRealisation;

    private List<Alerte> alertes;
    private List<LigneCommercial> parCommercial;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Alerte {
        private String type;
        /** URGENT ou ATTENTION. */
        private String gravite;
        private String commercial;
        private Long medecinId;
        private String medecinNom;
        private Long actionId;
        private Long retourTerrainId;
        private Long reclamationId;
        private String libelle;
        private String detail;
        private LocalDate date;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LigneCommercial {
        private String commercial;
        private long actionsDuCycle;
        private long actionsVisitees;
        private long actionsNonVisitees;
        private double tauxRealisation;
        private long retoursAQualifier;
        private long reclamationsEnAttente;
    }
}
