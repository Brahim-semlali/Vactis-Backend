package com.vactis.service;

import com.vactis.dto.bridgetogoal.BridgeToGoalResponse;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.reclamation.StatutReclamation;
import com.vactis.model.system.SystemSettings;
import com.vactis.repository.ActionRepository;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.ReclamationRepository;
import com.vactis.service.system.SystemSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BridgeToGoalServiceTest {

    @Mock
    private MedecinRepository medecinRepository;

    @Mock
    private ExtractionDonneesRepository extractionDonneesRepository;

    @Mock
    private ActionRepository actionRepository;

    @Mock
    private ReclamationRepository reclamationRepository;

    @Mock
    private SystemSettingsService systemSettingsService;

    @InjectMocks
    private BridgeToGoalService bridgeToGoalService;

    private Medecin medecinOnboarding;
    private Medecin medecinRetention;
    private Medecin medecinDev;

    @BeforeEach
    void setUp() {
        medecinOnboarding = new Medecin();
        medecinOnboarding.setId(1L);
        medecinOnboarding.setStatut("ONBOARDING");

        medecinRetention = new Medecin();
        medecinRetention.setId(2L);
        medecinRetention.setStatut("RETENTION");

        medecinDev = new Medecin();
        medecinDev.setId(3L);
        medecinDev.setStatut("ACTIF_STABLE");
        medecinDev.setSegment("A");
    }

    @Test
    @DisplayName("Simulation de la trajectoire budgétaire avec calcul du gap et des actions requises")
    void testSimulerTrajectoire() {
        when(extractionDonneesRepository.sumPrixAPayerByDateRange(any(), any())).thenReturn(40000L);
        when(medecinRepository.findAll()).thenReturn(List.of(medecinOnboarding, medecinRetention, medecinDev));
        when(reclamationRepository.countByStatut(StatutReclamation.OUVERTE)).thenReturn(1L);

        BridgeToGoalResponse res = bridgeToGoalService.simulerTrajectoire(60000L, "2024-05");

        assertNotNull(res);
        assertEquals(40000L, res.getCaActuel());
        assertEquals(1, res.getNbMedecinsOnboarding());
        assertEquals(1, res.getNbMedecinsEnRetention());
        assertEquals(1, res.getNbMedecinsDeveloppement());
        assertEquals(1, res.getNbReclamationsBloquantes());

        assertTrue(res.getGainOnboarding() > 0);
        assertTrue(res.getGainRetention() > 0);
        assertTrue(res.getGainDeveloppement() > 0);
        assertTrue(res.getChurnEstime() > 0);

        assertTrue(res.getCaProjete() > 0);
        assertEquals(60000L, res.getTargetBudget());
        assertTrue(res.getGap() >= 0);
        assertNotNull(res.getStatutTrajectoire());
    }

    @Test
    @DisplayName("Quand aucun target n'est fourni, le service utilise l'objectif global configuré par l'admin")
    void testUsesConfiguredSystemTargetWhenNoTargetIsProvided() {
        when(extractionDonneesRepository.sumPrixAPayerByDateRange(any(), any())).thenReturn(40000L);
        when(medecinRepository.findAll()).thenReturn(List.of(medecinOnboarding, medecinRetention, medecinDev));
        when(reclamationRepository.countByStatut(StatutReclamation.OUVERTE)).thenReturn(0L);

        SystemSettings settings = new SystemSettings();
        settings.setBridgeGoalTarget(60000L);
        when(systemSettingsService.getSettings()).thenReturn(settings);

        BridgeToGoalResponse res = bridgeToGoalService.simulerTrajectoire(null, "2024-05");

        assertNotNull(res);
        assertEquals(60000L, res.getTargetBudget());
    }
}
