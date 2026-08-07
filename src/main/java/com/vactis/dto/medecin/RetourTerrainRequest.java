package com.vactis.dto.medecin;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;

import java.time.LocalDate;

/**
 * Corps de la requête POST /medecins/{id}/retours-terrain.
 * Crée toujours une NOUVELLE ligne historisée — jamais une mise à jour.
 */
@Data
public class RetourTerrainRequest {

    /** Note de la visite sur 5 (obligatoire, entre 1.0 et 5.0 inclus). */
    @NotNull(message = "La note est obligatoire.")
    @DecimalMin(value = "1.0", message = "La note doit être au minimum 1.")
    @DecimalMax(value = "5.0", message = "La note doit être au maximum 5.")
    private Double note;

    /** Date de la visite (obligatoire, passée ou aujourd'hui — jamais future). */
    @NotNull(message = "La date de visite est obligatoire.")
    @PastOrPresent(message = "La date de visite ne peut pas être dans le futur.")
    private LocalDate dateVisite;

    /** Commentaire libre, optionnel. */
    private String commentaire;

    /** Visiteur médical (optionnel, pré-rempli avec l'utilisateur connecté si non spécifié). */
    private String visiteur;

    /** Statut d'exécution : REALISEE, NON_REALISEE, NON_RENSEIGNE (optionnel, défaut REALISEE). */
    private String statutVisite;

    /** Qualification du retour : FAVORABLE, DEFAVORABLE, NEUTRE, NON_RENSEIGNE (optionnel, défaut NON_RENSEIGNE). */
    private String qualification;

    /** Visite avec réclamation médecin (optionnel, défaut false). */
    private Boolean reclamation;
}
