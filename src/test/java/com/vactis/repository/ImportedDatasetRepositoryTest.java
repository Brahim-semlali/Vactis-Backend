package com.vactis.repository;

import com.vactis.model.action.Action;
import com.vactis.model.action.UrgenceAction;
import com.vactis.model.data.ExtractionDonnees;
import com.vactis.model.data.StatutDossier;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.RisqueUrgence;
import com.vactis.model.medecin.StatutPilotage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class ImportedDatasetRepositoryTest {

    @Autowired
    private MedecinRepository medecinRepository;

    @Autowired
    private ExtractionDonneesRepository extractionDonneesRepository;

    @Autowired
    private ActionRepository actionRepository;

    @Test
    void portfolioQueriesOnlyReturnDoctorsPresentInImportedDataset() {
        Medecin importedDoctor = medecinRepository.saveAndFlush(doctor("MED001", "Dans fichier"));
        Medecin legacyDoctor = medecinRepository.saveAndFlush(doctor("MED002", "Ancien profil"));
        extractionDonneesRepository.saveAndFlush(dossier(importedDoctor, "IMPORT-1"));
        actionRepository.saveAndFlush(action(importedDoctor));
        actionRepository.saveAndFlush(action(legacyDoctor));

        assertEquals(List.of(importedDoctor.getId()), medecinRepository.findAllWithImportedDossiers().stream()
                .map(Medecin::getId)
                .toList());
        assertEquals(List.of(importedDoctor.getId()), medecinRepository.searchMedecins(
                        null, null, null, null, null, null, null, null).stream()
                .map(Medecin::getId)
                .toList());
        assertEquals(List.of(importedDoctor.getId(), legacyDoctor.getId()), actionRepository.findAll().stream()
                .map(action -> action.getMedecin().getId())
                .sorted()
                .toList());
        assertEquals(List.of(importedDoctor.getId()), actionRepository.findAllForImportedDoctors().stream()
                .map(action -> action.getMedecin().getId())
                .toList());
    }

    @Test
    void onboardingSearchIncludesDoctorsWithoutImportedDossiers() {
        Medecin importedOnboarding = doctor("MED003", "Onboarding importé");
        importedOnboarding.setStatut("ONBOARDING");
        Medecin onboardingWithoutDossier = doctor("MED004", "Onboarding sans dossier");
        onboardingWithoutDossier.setStatut("ONBOARDING");
        importedOnboarding = medecinRepository.saveAndFlush(importedOnboarding);
        onboardingWithoutDossier = medecinRepository.saveAndFlush(onboardingWithoutDossier);
        extractionDonneesRepository.saveAndFlush(dossier(importedOnboarding, "IMPORT-3"));

        assertEquals(List.of(importedOnboarding.getId(), onboardingWithoutDossier.getId()),
                medecinRepository.searchMedecins(null, null, "ONBOARDING", null, null, null, null, null).stream()
                        .map(Medecin::getId)
                        .sorted()
                        .toList());
    }

    private static Medecin doctor(String code, String nom) {
        Medecin medecin = new Medecin();
        medecin.setCodeMedecin(code);
        medecin.setNom(nom);
        medecin.setPrenom("Test");
        medecin.setSpecialite("Cardiologie");
        medecin.setOrganisme("Clinique Test");
        medecin.setStatut("ACTIF_STABLE");
        medecin.setStatutPilotage(StatutPilotage.ACTIF_STABLE);
        medecin.setRisqueUrgence(RisqueUrgence.FAIBLE);
        return medecin;
    }

    private static ExtractionDonnees dossier(Medecin medecin, String reference) {
        ExtractionDonnees dossier = new ExtractionDonnees();
        dossier.setMedecin(medecin);
        dossier.setReferenceDossier(reference);
        dossier.setDateReception(LocalDate.of(2026, 9, 30));
        dossier.setDatePrelevement(LocalDate.of(2026, 9, 30));
        dossier.setTypeAnalyse("Cardiologie");
        dossier.setNombreAnalyses(1);
        dossier.setStatutDossier(StatutDossier.TERMINE);
        dossier.setLieuPrelevement("Clinique Test");
        dossier.setPrixTotal(1000);
        dossier.setMontantRembourse(200);
        dossier.setPrixAPayer(800);
        dossier.setUrgent(false);
        return dossier;
    }

    private static Action action(Medecin medecin) {
        Action action = new Action();
        action.setMedecin(medecin);
        action.setStatut("ACTIF_STABLE");
        action.setActionRecommandee("visite suivi régulier");
        action.setUrgence(UrgenceAction.FAIBLE);
        action.setCommercial("Test");
        action.setLieuOrganisme("Clinique Test");
        action.setBacklog(false);
        action.setUrgenceSilence(false);
        return action;
    }
}