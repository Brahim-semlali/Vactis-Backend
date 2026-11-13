package com.vactis.service.Activite;

import com.vactis.model.medecin.QualificationVisite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualificationEvolutionPolicyTest {

    @Test
    void mapsDocumentedSixCases() {
        assertEquals("SUCCES_CONFIRME", QualificationEvolutionPolicy.comparer(
                QualificationVisite.FAVORABLE, "FAVORABLE", "PROGRESSION").diagnostic());
        assertEquals("FAUX_POSITIF", QualificationEvolutionPolicy.comparer(
                QualificationVisite.FAVORABLE, "STABLE", "RETENTION").diagnostic());
        assertEquals("PERTE_CONFIRMEE", QualificationEvolutionPolicy.comparer(
                QualificationVisite.DEFAVORABLE, "DEFAVORABLE", "RETENTION").diagnostic());
        assertEquals("RESILIENCE_INATTENDUE", QualificationEvolutionPolicy.comparer(
                QualificationVisite.DEFAVORABLE, "FAVORABLE", "PROGRESSION").diagnostic());
        assertTrue(QualificationEvolutionPolicy.comparer(
                QualificationVisite.NEUTRE, "STABLE", "ACTIF_STABLE").concordant());
        assertEquals("NON_OBSERVABLE", QualificationEvolutionPolicy.comparer(
                QualificationVisite.RECLAMATION, "NON_OBSERVABLE", null).diagnostic());
    }
}