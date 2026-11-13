package com.vactis.repository;

import com.vactis.model.medecin.Medecin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

class MedecinPseudoFilterTest {

    private final MedecinRepository repository = mock(MedecinRepository.class, CALLS_REAL_METHODS);

    @Test
    void detectsPseudoDoctorsRegardlessOfAccentsCaseAndNameOrder() {
        assertTrue(repository.isPseudoMedecin(doctor("Non", "Précisé")));
        assertTrue(repository.isPseudoMedecin(doctor("INCONNU", "Médecin")));
        assertTrue(repository.isPseudoMedecin(doctor("Médecin", "Inconnu")));
        assertTrue(repository.isPseudoMedecin(doctor("N/R", "")));
        assertTrue(repository.isPseudoMedecin(doctor("NR", "")));
        assertFalse(repository.isPseudoMedecin(doctor("Inconnu", "Mohamed")));
        assertFalse(repository.isPseudoMedecin(doctor("Oulad", "Badr")));
    }

    private Medecin doctor(String nom, String prenom) {
        Medecin medecin = new Medecin();
        medecin.setNom(nom);
        medecin.setPrenom(prenom);
        return medecin;
    }
}