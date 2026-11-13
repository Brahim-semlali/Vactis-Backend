package com.vactis.service.Activite;

import com.vactis.model.action.StatutPlanRetention;
import com.vactis.model.action.TypeEtapeRetention;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.QualificationVisite;
import com.vactis.model.medecin.RetourTerrain;
import com.vactis.repository.ActionRepository;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.RetourTerrainRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivitePortefeuilleServiceTest {

    @Mock private ExtractionDonneesRepository extractionDonneesRepository;
    @Mock private MedecinRepository medecinRepository;
    @Mock private RetourTerrainRepository retourTerrainRepository;
    @Mock private ActionRepository actionRepository;

    @InjectMocks private ActivitePortefeuilleService service;

    private Medecin medecin;
    private List<LocalDate> dates;

    @BeforeEach
    void setUp() {
        medecin = new Medecin();
        medecin.setId(10L);
        medecin.setStatut("RETENTION");
        medecin.setDatePremiereCollaboration(LocalDate.now().minusMonths(12));
        medecin.setIsAReactiverManuel(false);
        dates = List.of(
                LocalDate.now().minusDays(10),
                LocalDate.now().minusMonths(4),
                LocalDate.now().minusMonths(10));
        lenient().when(extractionDonneesRepository.findDatesReceptionByMedecinId(10L)).thenReturn(dates);
        lenient().when(retourTerrainRepository.findByMedecinOrderByDateVisiteDescCreatedAtDesc(medecin)).thenReturn(List.of());
        lenient().when(actionRepository.existsRetentionRecovery(eq(10L), anyList(), eq(StatutPlanRetention.CLOTURE)))
                .thenReturn(false);
    }

    @Test
    void acceptsEligibleIrregularProfileUsingAllActivityHistory() {
        assertTrue(service.estProfilActiviteIrreguliere(medecin, "RETENTION", LocalDate.now()));
    }

    @Test
    void rejectsCurrentJustifiedAbsence() {
        RetourTerrain absence = new RetourTerrain();
        absence.setQualification(QualificationVisite.CONGE_ABSENCE);
        absence.setDateDepart(LocalDate.now().minusDays(3));
        absence.setDateRetourPrevue(LocalDate.now().plusDays(10));
        when(retourTerrainRepository.findByMedecinOrderByDateVisiteDescCreatedAtDesc(medecin))
                .thenReturn(List.of(absence));

        assertFalse(service.estProfilActiviteIrreguliere(medecin, "RETENTION", LocalDate.now()));
    }

    @Test
    void rejectsConfirmedRecoveryFromEitherRetentionStep() {
        when(actionRepository.existsRetentionRecovery(
                eq(10L), eq(List.of(TypeEtapeRetention.ACTION_1, TypeEtapeRetention.ACTION_2)),
                eq(StatutPlanRetention.CLOTURE))).thenReturn(true);

        assertFalse(service.estProfilActiviteIrreguliere(medecin, "RETENTION", LocalDate.now()));
    }

    @Test
    void monthlyInactiveWindowExcludesActivityExactlySixtyDaysBeforeMonthEnd() {
        var month = java.time.YearMonth.of(2026, 6);
        var monthEnd = month.atEndOfMonth();
        medecin.setDatePremiereCollaboration(LocalDate.of(2025, 1, 1));
        when(medecinRepository.isPseudoMedecin(medecin)).thenReturn(false);
        lenient().when(extractionDonneesRepository.sumCaByMedecinAndDateRange(any(), any())).thenReturn(List.of());
        lenient().when(extractionDonneesRepository.countCasByMedecinAndDateRange(any(), any())).thenReturn(List.of());
        when(extractionDonneesRepository.findMedecinIdsWithActivityInRange(monthEnd.minusDays(59), monthEnd))
                .thenReturn(List.of());

        var statuses = service.buildStatutMapForMonth(month, List.of(medecin));

        assertEquals("inactif", statuses.get(medecin.getId()));
        verify(extractionDonneesRepository).findMedecinIdsWithActivityInRange(monthEnd.minusDays(59), monthEnd);
    }
}