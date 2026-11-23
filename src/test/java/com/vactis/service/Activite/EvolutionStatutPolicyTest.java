package com.vactis.service.Activite;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvolutionStatutPolicyTest {

    @Test
    void compareLesRangsDeStatut() {
        assertEquals(EvolutionStatutPolicy.FAVORABLE, EvolutionStatutPolicy.evolution("surveillance", "progression", true));
        assertEquals(EvolutionStatutPolicy.STABLE, EvolutionStatutPolicy.evolution("ACTIF_STABLE", "actif_stable", true));
        assertEquals(EvolutionStatutPolicy.DEFAVORABLE, EvolutionStatutPolicy.evolution("actif_stable", "retention", true));
    }

    @Test
    void statutInconnuEstTraiteCommeExcluEtNonObservablePrime() {
        assertEquals(EvolutionStatutPolicy.STABLE, EvolutionStatutPolicy.evolution(null, "exclu", true));
        assertEquals(EvolutionStatutPolicy.NON_OBSERVABLE, EvolutionStatutPolicy.evolution("exclu", "progression", false));
    }
}
