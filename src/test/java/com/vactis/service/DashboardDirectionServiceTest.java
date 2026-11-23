package com.vactis.service;

import com.vactis.dto.activite.RapportImpactResponse;
import com.vactis.dto.bridgetogoal.BridgeToGoalResponse;
import com.vactis.dto.direction.DashboardDirectionResponse;
import com.vactis.model.action.Action;
import com.vactis.model.action.EtatAction;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.StatutPilotage;
import com.vactis.model.reclamation.PrioriteReclamation;
import com.vactis.model.reclamation.Reclamation;
import com.vactis.model.reclamation.StatutReclamation;
import com.vactis.repository.ActionRepository;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.ReclamationRepository;
import com.vactis.service.Activite.ActiviteImpactService;
import com.vactis.service.Activite.ActivitePortefeuilleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardDirectionServiceTest {

    @Mock private ExtractionDonneesRepository extractionDonneesRepository;
    @Mock private MedecinRepository medecinRepository;
    @Mock private ActionRepository actionRepository;
    @Mock private ReclamationRepository reclamationRepository;
    @Mock private ActiviteImpactService activiteImpactService;
    @Mock private BridgeToGoalService bridgeToGoalService;
    @Mock private ActivitePortefeuilleService portefeuilleService;

    @InjectMocks private DashboardDirectionService service;

    @BeforeEach
    void setUp() {
        when(extractionDonneesRepository.sumPrixAPayerByDateRange(any(), any())).thenReturn(0L);
        when(extractionDonneesRepository.countCasByDateRange(any(), any())).thenReturn(0L);
        when(extractionDonneesRepository.sumPrixAPayerNonAffectesByDateRange(any(), any())).thenReturn(0L);
        when(extractionDonneesRepository.sumPrixAPayerWithMedecinByDateRange(any(), any())).thenReturn(0L);
        when(activiteImpactService.getRapportImpact("2026-10")).thenReturn(RapportImpactResponse.builder()
                .mois("2026-10").actionsVactisGenerees(10).vactisRealisees(6).vactisNonRealisees(1)
                .sanRetourTerrain(3).visitesHorsVactisRealisees(4).tauxRealisation(60.0).build());
        when(medecinRepository.findAll()).thenReturn(List.of());
        when(actionRepository.findAll()).thenReturn(List.of());
        when(reclamationRepository.findAll()).thenReturn(List.of());
        when(portefeuilleService.buildStatutMapForMonth(any(), any())).thenReturn(Map.of());
        when(extractionDonneesRepository.sumCaByMedecinAndDateRange(any(), any())).thenReturn(List.of());
        when(extractionDonneesRepository.findDatesReceptionByMedecinId(any())).thenReturn(List.of());
    }

    @Test
    void situationMensuelleTraduitLeStatutVactisDuMois() {
        assertEquals("RETENTION", DashboardDirectionService.situationMensuelle("retention"));
        assertEquals("SILENCE", DashboardDirectionService.situationMensuelle("a_reactiver"));
        assertEquals("SURVEILLANCE", DashboardDirectionService.situationMensuelle("surveillance"));
        assertNull(DashboardDirectionService.situationMensuelle("actif_stable"));
        assertNull(DashboardDirectionService.situationMensuelle(null));
    }

    @Test
    void lePortefeuilleDependDuMoisChoisi() {
        Medecin m = medecin("A", StatutPilotage.ACTIF_STABLE, null);
        m.setId(42L);
        when(medecinRepository.findAll()).thenReturn(List.of(m));
        when(bridgeToGoalService.simulerTrajectoire(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "objectif absent"));
        when(activiteImpactService.getRapportImpact(any())).thenReturn(RapportImpactResponse.builder().build());
        // En septembre le médecin est en rétention, en octobre il est revenu à un statut stable
        when(portefeuilleService.buildStatutMapForMonth(eq(YearMonth.of(2026, 9)), any())).thenReturn(Map.of(42L, "retention"));
        when(portefeuilleService.buildStatutMapForMonth(eq(YearMonth.of(2026, 10)), any())).thenReturn(Map.of(42L, "actif_stable"));
        // CA de référence de septembre = moyenne juin–août (9 000 / 3)
        when(extractionDonneesRepository.sumCaByMedecinAndDateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31)))
                .thenReturn(List.<Object[]>of(new Object[]{42L, 9_000L}));
        when(extractionDonneesRepository.findDatesReceptionByMedecinId(42L))
                .thenReturn(List.of(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 8, 20)));

        DashboardDirectionResponse septembre = service.getDashboard("2026-09");
        DashboardDirectionResponse octobre = service.getDashboard("2026-10");

        assertEquals(1, septembre.getPortefeuille().getTotalMedecins());
        DashboardDirectionResponse.MedecinPrioritaire p = septembre.getPortefeuille().getPrioritaires().get(0);
        assertEquals("RETENTION", p.getSituation());
        assertEquals(3_000L, p.getCaReference());
        // Dernier dossier au 20/08 (celui du 03/10 est postérieur à septembre) -> 41 jours au 30/09
        assertEquals(41, p.getJoursSansActivite());
        assertEquals(0, octobre.getPortefeuille().getTotalMedecins());
    }

    @Test
    void segmentEtEvolutionSontNormalises() {
        assertEquals("A", DashboardDirectionService.segmentNormalise("Segment A"));
        assertEquals("B", DashboardDirectionService.segmentNormalise(" b "));
        assertNull(DashboardDirectionService.evolutionPct(100, 0));
        assertEquals(25.0, DashboardDirectionService.evolutionPct(125, 100));
    }

    @Test
    void sansObjectifLaSyntheseResteDisponibleEtSignaleLeManque() {
        when(bridgeToGoalService.simulerTrajectoire("2026-10"))
                .thenThrow(new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "objectif absent"));
        when(extractionDonneesRepository.sumPrixAPayerByDateRange(eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31))))
                .thenReturn(528_570L);
        when(extractionDonneesRepository.sumPrixAPayerByDateRange(eq(LocalDate.of(2025, 10, 1)), eq(LocalDate.of(2025, 10, 31))))
                .thenReturn(500_000L);

        DashboardDirectionResponse response = service.getDashboard("2026-10");

        assertEquals("2026-10", response.getMois());
        assertFalse(response.getTrajectoire().getObjectifDefini());
        assertNull(response.getTrajectoire().getEcartObjectifMois());
        assertEquals(528_570L, response.getTrajectoire().getCaMois());
        assertEquals(500_000L, response.getTrajectoire().getCaMoisNMoins1());
        assertEquals(5.7, response.getTrajectoire().getEvolutionMoisNMoins1Pct());
        assertEquals(6, response.getTrajectoire().getTendance().size());
        assertTrue(response.getSujets().stream().anyMatch(s -> s.getTitre().equals("Objectif non paramétré")));
    }

    @Test
    void calculeEcartsPortefeuilleEtExecution() {
        when(bridgeToGoalService.simulerTrajectoire("2026-10")).thenReturn(BridgeToGoalResponse.builder()
                .monthlyTargetBudget(600_000L).objectifYtdProratise(6_000_000L).annualTargetBudget(7_200_000L)
                .caPortefeuille(511_380L).statutYtd("RETARD IMPORTANT").build());
        when(extractionDonneesRepository.sumPrixAPayerByDateRange(eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31))))
                .thenReturn(528_570L);
        when(extractionDonneesRepository.sumPrixAPayerByDateRange(eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 10, 31))))
                .thenReturn(5_000_000L);

        Medecin retentionA = medecin("A", StatutPilotage.ACTIF_STABLE, null);
        retentionA.setId(1L);
        Medecin silenceB = medecin("B", StatutPilotage.ACTIF_STABLE, null);
        silenceB.setId(2L);
        Medecin segmentC = medecin("C", StatutPilotage.ACTIF_STABLE, null);
        segmentC.setId(3L);
        when(medecinRepository.findAll()).thenReturn(List.of(retentionA, silenceB, segmentC));
        when(portefeuilleService.buildStatutMapForMonth(eq(YearMonth.of(2026, 10)), any()))
                .thenReturn(Map.of(1L, "retention", 2L, "a_reactiver", 3L, "retention"));
        when(extractionDonneesRepository.sumCaByMedecinAndDateRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 60_000L}, new Object[]{2L, 24_000L}));

        Action enRetard = new Action();
        enRetard.setCycleMensuel("2026-10");
        enRetard.setEtatAction(EtatAction.PLANIFIEE);
        enRetard.setEstEnRetard(true);
        when(actionRepository.findAll()).thenReturn(List.of(enRetard));

        Reclamation echue = new Reclamation();
        echue.setStatut(StatutReclamation.EN_COURS);
        echue.setPriorite(PrioriteReclamation.HAUTE);
        echue.setDateEcheance(LocalDate.now().minusDays(2));
        Reclamation fermee = new Reclamation();
        fermee.setStatut(StatutReclamation.TRAITEE);
        when(reclamationRepository.findAll()).thenReturn(List.of(echue, fermee));

        DashboardDirectionResponse response = service.getDashboard("2026-10");

        DashboardDirectionResponse.Trajectoire t = response.getTrajectoire();
        assertTrue(t.getObjectifDefini());
        assertEquals(-71_430L, t.getEcartObjectifMois());
        assertEquals(-1_000_000L, t.getEcartObjectifCumule());
        assertEquals(6_000_000L, t.getProjectionAnnuelle());

        DashboardDirectionResponse.Portefeuille p = response.getPortefeuille();
        assertEquals(2, p.getTotalMedecins());
        assertEquals(28_000L, p.getTotalCaReference());
        assertEquals(1, p.getSegments().get(0).getRetention().getNombre());
        assertEquals(1, p.getSegments().get(1).getSilence().getNombre());
        assertEquals("RETENTION", p.getPrioritaires().get(0).getSituation());

        DashboardDirectionResponse.Execution e = response.getExecution();
        assertEquals(10L, e.getActionsDuCycle());
        assertEquals(6L, e.getVisitesVactisRealisees());
        assertEquals(4L, e.getVisitesLibresRealisees());
        assertEquals(1L, e.getActionsEnRetard());
        assertEquals(1L, e.getReclamationsOuvertes());
        assertEquals(1L, e.getReclamationsEcheanceDepassee());

        assertEquals("CRITIQUE", response.getSujets().get(0).getNiveau());
        assertTrue(response.getSujets().stream().anyMatch(s -> s.getTitre().startsWith("Retard sur l'objectif")));
    }

    private static Medecin medecin(String segment, StatutPilotage statut, String classeSilence) {
        Medecin m = new Medecin();
        m.setId((long) (Math.random() * 100000));
        m.setNom("Nom" + segment);
        m.setSegment(segment);
        m.setStatutPilotage(statut);
        m.setClasseSilence(classeSilence);
        return m;
    }
}
