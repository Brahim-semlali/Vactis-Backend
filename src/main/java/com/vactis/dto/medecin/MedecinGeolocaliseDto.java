package com.vactis.dto.medecin;

public record MedecinGeolocaliseDto(
        Long id,
        String codeMedecin,
        String nom,
        String prenom,
        String specialite,
        String organisme,
        String segment,
        String statut,
        Integer caMobile,
        Double latitude,
        Double longitude
) {}
