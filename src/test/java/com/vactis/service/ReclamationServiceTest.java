package com.vactis.service;

import com.vactis.dto.reclamation.ReclamationRequest;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.reclamation.CategorieReclamation;
import com.vactis.model.reclamation.PrioriteReclamation;
import com.vactis.model.reclamation.Reclamation;
import com.vactis.model.reclamation.StatutReclamation;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.ReclamationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReclamationServiceTest {

    @Mock
    private ReclamationRepository reclamationRepository;

    @Mock
    private MedecinRepository medecinRepository;

    @InjectMocks
    private ReclamationService reclamationService;

    private Medecin medecin;

    @BeforeEach
    void setUp() {
        medecin = new Medecin();
        medecin.setId(5L);
        medecin.setNom("Lemoine");
    }

    @Test
    @DisplayName("Création d'un ticket réclamation avec statut OUVERTE")
    void testCreerReclamation() {
        ReclamationRequest request = new ReclamationRequest();
        request.setMedecinId(5L);
        request.setDescription("Délais trop longs sur les biopsies");
        request.setCategorie(CategorieReclamation.DELAIS_RESULTATS);
        request.setPriorite(PrioriteReclamation.HAUTE);

        when(medecinRepository.findById(5L)).thenReturn(Optional.of(medecin));
        when(reclamationRepository.save(any(Reclamation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Reclamation rec = reclamationService.creerReclamation(request, "commercial1");

        assertNotNull(rec);
        assertEquals(StatutReclamation.OUVERTE, rec.getStatut());
        assertEquals("commercial1", rec.getCommercialDeclarant());
        assertEquals(CategorieReclamation.DELAIS_RESULTATS, rec.getCategorie());
        assertEquals(PrioriteReclamation.HAUTE, rec.getPriorite());
    }

    @Test
    @DisplayName("Mise à jour du statut vers RESOLUE avec horodatage de résolution")
    void testMettreAJourStatut_VersResolue() {
        Reclamation rec = new Reclamation();
        rec.setId(10L);
        rec.setStatut(StatutReclamation.OUVERTE);

        when(reclamationRepository.findById(10L)).thenReturn(Optional.of(rec));
        when(reclamationRepository.save(any(Reclamation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Reclamation miseAJour = reclamationService.mettreAJourStatut(10L, StatutReclamation.RESOLUE, "Protocole express activé", "Dr Responsable Qualité");

        assertEquals(StatutReclamation.RESOLUE, miseAJour.getStatut());
        assertEquals("Protocole express activé", miseAJour.getSolutionApportee());
        assertNotNull(miseAJour.getDateResolution());
    }
}
