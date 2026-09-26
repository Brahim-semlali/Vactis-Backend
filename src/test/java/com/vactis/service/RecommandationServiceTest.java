package com.vactis.service;

import com.vactis.dto.recommandation.RecommandationsGlobalResponse;
import com.vactis.model.action.Action;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.RisqueUrgence;
import com.vactis.repository.ActionRepository;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommandationServiceTest {

    @Mock
    private MedecinRepository medecinRepository;

    @Mock
    private ExtractionDonneesRepository extractionDonneesRepository;

    @Mock
    private ActionRepository actionRepository;

    @InjectMocks
    private RecommandationService recommandationService;

    private Medecin medecinDev;
    private Medecin medecinReact;
    private Medecin medecinIrreg;

    @BeforeEach
    void setUp() {
        medecinDev = new Medecin();
        medecinDev.setId(1L);
        medecinDev.setNom("Dupont");
        medecinDev.setSegment("A");
        medecinDev.setStatut("ACTIF_STABLE");
        medecinDev.setNoteInput(4.0);
        medecinDev.setReferenceCa(100.0);
        medecinDev.setCaMensuelMoyen(50.0);

        medecinReact = new Medecin();
        medecinReact.setId(2L);
        medecinReact.setNom("Martin");
        medecinReact.setSegment("C");
        medecinReact.setStatut("INACTIF");
        medecinReact.setRisqueUrgence(RisqueUrgence.ELEVE);

        medecinIrreg = new Medecin();
        medecinIrreg.setId(3L);
        medecinIrreg.setNom("Chirurgien");
        medecinIrreg.setSegment("B");
        medecinIrreg.setStatut("ACTIF_STABLE");
        medecinIrreg.setIsProfilIrregulier(true);
    }

    @Test
    @DisplayName("Tri des prescripteurs dans les 3 listes de recommandations")
    void testGetRecommandations_RepartitCorrectement() {
        when(medecinRepository.findAll()).thenReturn(List.of(medecinDev, medecinReact, medecinIrreg));
        when(extractionDonneesRepository.sumCaByMedecinAndDateRange(any(), any())).thenReturn(List.of());
        when(extractionDonneesRepository.countCasGroupedByMedecin()).thenReturn(List.of(
                new Object[]{1L, 20L},
                new Object[]{2L, 15L},
                new Object[]{3L, 30L}
        ));

        RecommandationsGlobalResponse response = recommandationService.getRecommandations();

        assertNotNull(response);
        assertEquals(1, response.getDeveloppement().size());
        assertEquals("Dupont", response.getDeveloppement().get(0).getNomMedecin().replace("Dr Dupont ", "Dupont"));
        assertEquals(1, response.getReactivation().size());
        assertEquals(1, response.getIrreguliersRentables().size());
        assertEquals(3, response.getTotalOpportunites());
    }

    @Test
    @DisplayName("Création d'une action à partir d'une recommandation de développement")
    void testCreerActionDepuisRecommandation() {
        when(medecinRepository.findById(1L)).thenReturn(Optional.of(medecinDev));
        when(actionRepository.save(any(Action.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Action action = recommandationService.creerActionDepuisRecommandation(1L, "DEVELOPPEMENT", "commercial1");

        assertNotNull(action);
        assertEquals(30, action.getHorizonJours());
        assertTrue(action.getActionRecommandee().contains("Opportunité de développement"));
    }
}
