package com.vactis.dto.medecin;

public record MedecinSansLocalisationDto(
        Long id,
        String codeMedecin,
        String nom,
        String prenom,
        String specialite,
        String organisme,
        String ville,
        String segment,
        String statut
) {}
