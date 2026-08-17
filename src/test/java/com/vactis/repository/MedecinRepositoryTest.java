package com.vactis.repository;

import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.RisqueUrgence;
import com.vactis.model.medecin.StatutMedecin;
import com.vactis.model.medecin.StatutPilotage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class MedecinRepositoryTest {

    @Autowired
    private MedecinRepository medecinRepository;

    private Medecin createMedecin(String code, String nom) {
        Medecin medecin = new Medecin();

        medecin.setCodeMedecin(code);
        medecin.setNom(nom);
        medecin.setPrenom("Ahmed");
        medecin.setSpecialite("Cardiologie");
        medecin.setOrganisme("CHU Marrakech");
        medecin.setVille("Marrakech");
        medecin.setTelephone("0612345678");
        medecin.setEmail("ahmed@test.com");
        medecin.setStatut(StatutMedecin.NOUVEAU.name());        medecin.setSegment("A");
        medecin.setNoteInput(8.0);
        medecin.setScoreValeur(80.0);
        medecin.setStatutPilotage(StatutPilotage.ACTIF);
        medecin.setRisqueUrgence(RisqueUrgence.FAIBLE);
        medecin.setCaMois(10000);
        medecin.setCommercialReferent("Commercial 1");
        medecin.setCommentaire("Test");

        return medecin;
    }

    @Test
    void shouldFindMedecinByCode() {
        Medecin medecin = createMedecin("MED001", "Benali");

        medecinRepository.save(medecin);

        Optional<Medecin> result = medecinRepository.findByCodeMedecin("MED001");

        assertTrue(result.isPresent());
        assertEquals("MED001", result.get().getCodeMedecin());
    }


    @Test
    void shouldNotFindMedecinByCode() {

        Optional<Medecin> result = medecinRepository.findByCodeMedecin("MED002");

        assertFalse(result.isPresent());

    }

    @Test
    void shouldFindByStatutPilotage() {
        Medecin medecin = createMedecin("MED001", "Benali");

        medecinRepository.save(medecin);

        List<Medecin> result = medecinRepository.findAllByStatutPilotage(StatutPilotage.ACTIF);

        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals("MED001", result.get(0).getCodeMedecin());
    }

    @Test
    void shouldSearchMedecinsByStatut() {
        Medecin medecin = createMedecin("MED001", "Benali");

        medecinRepository.save(medecin);

        List<Medecin> result = medecinRepository.searchMedecins(null, null, "NOUVEAU", null, null, null, null);

        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals("MED001", result.get(0).getCodeMedecin());
    }
}