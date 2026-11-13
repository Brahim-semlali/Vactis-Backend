package com.vactis.service;

import com.vactis.model.medecin.MotifNonRealisation;
import com.vactis.model.medecin.ObstaclePrincipal;
import com.vactis.model.medecin.QualificationVisite;
import com.vactis.model.medecin.StatutPilotage;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessEnumComplianceTest {

    @Test
    void allDocumentedNonRealizationMotifsExist() {
        assertTrue(enumNames(MotifNonRealisation.class).containsAll(Set.of(
                "MEDECIN_ABSENT", "MANQUE_DE_TEMPS", "VISITE_REPORTEE", "PRIORITE_MODIFIEE",
                "ADRESSE_INCORRECTE_INDISPONIBLE", "AUTRE")));
    }

    @Test
    void allDocumentedDefavorableObstaclesExist() {
        assertTrue(enumNames(ObstaclePrincipal.class).containsAll(Set.of(
                "PRIX_TARIF", "QUALITE", "DELAI", "RELATION", "CONCURRENCE",
                "ORGANISATION_LOGISTIQUE", "REFUS_EXPLICITE", "AUTRE")));
    }

    @Test
    void documentedQualificationsAndPilotStatusesExist() {
        assertTrue(enumNames(QualificationVisite.class).containsAll(Set.of(
                "FAVORABLE", "NEUTRE", "DEFAVORABLE", "RECLAMATION", "CONGE_ABSENCE")));
        assertTrue(enumNames(StatutPilotage.class).containsAll(Set.of(
                "PROGRESSION", "ACTIF_STABLE", "SURVEILLANCE", "RETENTION", "ONBOARDING",
                "ACTIVITE_IRREGULIERE", "INACTIF")));
    }

    private static Set<String> enumNames(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
    }
}