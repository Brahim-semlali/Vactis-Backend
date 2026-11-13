package com.vactis.service;

import com.vactis.dto.bridgetogoal.BridgeToGoalResponse;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.reclamation.StatutReclamation;
import com.vactis.model.system.SystemSettings;
import com.vactis.model.system.BridgeGoalTarget;
import com.vactis.repository.ActionRepository;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.ReclamationRepository;
import com.vactis.repository.system.BridgeGoalTargetRepository;
import com.vactis.service.system.SystemSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

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
    private BridgeGoalTargetRepository bridgeGoalTargetRepository;

    @Mock
    private SystemSettingsService systemSettingsService;

    @InjectMocks
    private BridgeToGoalService bridgeToGoalService;

    private Medecin medecinOnboarding;
    private Medecin medecinRetention;
    private Medecin medecinDev;

    private void stubOpenClaims(long activeCount) {
        lenient().when(reclamationRepository.countByStatut(StatutReclamation.A_TRAITER)).thenReturn(activeCount);
        lenient().when(reclamationRepository.countByStatut(StatutReclamation.OUVERTE)).thenReturn(0L);
    }

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
        when(medecinRepository.findAllWithImportedDossiers()).thenReturn(List.of(medecinOnboarding, medecinRetention, medecinDev));
        stubOpenClaims(1L);
        SystemSettings settings = new SystemSettings();
        settings.setBridgeGoalTarget(60000L);
        when(systemSettingsService.getSettings()).thenReturn(settings);

        BridgeToGoalResponse res = bridgeToGoalService.simulerTrajectoire("2024-05");

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

        assertEquals(1_200L, res.getGainOnboarding());
        assertEquals(1_080L, res.getGainRetention());
        assertEquals(800L, res.getGainDeveloppement());
        assertEquals(2_220L, res.getChurnEstime());
        assertEquals(40_860L, res.getCaProjete());
        assertEquals(0L, res.getGap());
        assertEquals(0, res.getActionsRequises());
        assertEquals(96_000L, res.getRunRateAnnuel());
        assertEquals(0L, res.getGapProjete());
        assertEquals(0L, res.getEffortAdditionnelMensuel());
        assertEquals(817.2, res.getTauxAtteinte(), 0.01);
        assertEquals("ATTEINT", res.getStatutTrajectoire());
        assertEquals(5_000L, res.getMonthlyTargetBudget());

        assertTrue(res.getCaProjete() > 0);
        assertEquals(60000L, res.getTargetBudget());
        assertEquals(60000L, res.getAnnualTargetBudget());
        assertEquals(25000L, res.getObjectifYtdProratise());
        assertEquals(15000L, res.getVarianceYtd());
        assertTrue(res.getGap() >= 0);
        assertNotNull(res.getStatutTrajectoire());
        assertFalse(res.getOpportunites().isEmpty());
        assertEquals(res.getGainOnboarding() + res.getGainRetention() + res.getGainDeveloppement(),
            res.getOpportunites().stream().mapToLong(item -> item.potentielTheorique()).sum());
    }

    @Test
    @DisplayName("Quand aucun target n'est fourni, le service utilise l'objectif global configuré par l'admin")
    void testUsesConfiguredSystemTargetWhenNoTargetIsProvided() {
        when(extractionDonneesRepository.sumPrixAPayerByDateRange(any(), any())).thenReturn(40000L);
        when(medecinRepository.findAllWithImportedDossiers()).thenReturn(List.of(medecinOnboarding, medecinRetention, medecinDev));
        stubOpenClaims(0L);

        SystemSettings settings = new SystemSettings();
        settings.setBridgeGoalTarget(60000L);
        when(systemSettingsService.getSettings()).thenReturn(settings);

        BridgeToGoalResponse res = bridgeToGoalService.simulerTrajectoire("2024-05");

        assertNotNull(res);
        assertEquals(60000L, res.getTargetBudget());
        assertEquals(5000L, res.getMonthlyTargetBudget());
    }

    @Test
    @DisplayName("Un objectif mensuel pilote le gap du mois sans remplacer l'objectif annuel administrateur")
    void usesMonthlyTargetOnlyForSelectedMonth() {
        when(extractionDonneesRepository.sumPrixAPayerByDateRange(any(), any())).thenReturn(40000L);
        when(medecinRepository.findAllWithImportedDossiers()).thenReturn(List.of(medecinOnboarding, medecinRetention, medecinDev));
        stubOpenClaims(0L);
        SystemSettings settings = new SystemSettings();
        settings.setBridgeGoalTarget(60000L);
        when(systemSettingsService.getSettings()).thenReturn(settings);
        BridgeGoalTarget monthlyTarget = new BridgeGoalTarget("2024-05", "PERCENTAGE",
            java.math.BigDecimal.valueOf(20), 40000L, 48000L);
        when(bridgeGoalTargetRepository.findByPeriod("2024-05")).thenReturn(Optional.of(monthlyTarget));

        BridgeToGoalResponse response = bridgeToGoalService.simulerTrajectoire("2024-05");

        assertEquals(48000L, response.getMonthlyTargetBudget());
        assertEquals(40000L, response.getPreviousMonthRevenue());
        assertEquals(48000L, response.getGap() + response.getCaProjete());
        assertEquals(60000L, response.getTargetBudget());
        assertEquals(60000L, response.getAnnualTargetBudget());
        assertEquals(0L, response.getGapProjete());
        assertEquals(25000L, response.getObjectifYtdProratise());
    }

    @Test
    @DisplayName("Les pseudo-médecins sont exclus des agrégats du Bridge to Goal")
    void excludesPseudoDoctorsFromPhysicianAggregates() {
        Medecin pseudo = new Medecin();
        pseudo.setId(4L);
        pseudo.setNom("Non précisé");
        pseudo.setStatut("ONBOARDING");
        pseudo.setSegment("A");
        when(medecinRepository.findAllWithImportedDossiers()).thenReturn(List.of(medecinOnboarding, medecinRetention, medecinDev, pseudo));
        lenient().when(medecinRepository.isPseudoMedecin(any(Medecin.class)))
            .thenAnswer(invocation -> invocation.getArgument(0) == pseudo);
        when(extractionDonneesRepository.sumPrixAPayerByDateRange(any(), any())).thenReturn(40000L);
        stubOpenClaims(0L);
        SystemSettings settings = new SystemSettings();
        settings.setBridgeGoalTarget(60000L);
        when(systemSettingsService.getSettings()).thenReturn(settings);

        BridgeToGoalResponse response = bridgeToGoalService.simulerTrajectoire("2024-05");

        assertEquals(3, response.getNbMedecinsActifs());
        assertEquals(1, response.getNbMedecinsOnboarding());
    }

    @Test
    @DisplayName("Le rapport refuse de calculer si l'objectif administrateur n'est pas configuré en base")
    void testMissingConfiguredTargetDoesNotUseCodeFallback() {
        when(systemSettingsService.getSettings()).thenReturn(new SystemSettings());

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> bridgeToGoalService.simulerTrajectoire("2026-05")
        );
        assertEquals(HttpStatus.PRECONDITION_FAILED, exception.getStatusCode());
    }
}
