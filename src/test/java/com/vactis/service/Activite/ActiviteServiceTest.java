package com.vactis.service.Activite;

import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.RetourTerrainRepository;
import com.vactis.model.medecin.Medecin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ActiviteServiceTest {

    @Mock
    private ExtractionDonneesRepository extractionDonneesRepository;

    @Mock
    private MedecinRepository medecinRepository;

    @Mock
    private RetourTerrainRepository retourTerrainRepository;

    @InjectMocks
    private ActiviteService activiteService;

    @Test
    void comparaisonCalculatesPreviousReferenceAndVariations() {
        when(extractionDonneesRepository.countCasByDateRange(any(), any()))
                .thenAnswer(invocation -> casesFor(invocation.getArgument(0)));
        when(extractionDonneesRepository.sumPrixAPayerByDateRange(any(), any()))
                .thenAnswer(invocation -> caFor(invocation.getArgument(0)));

        var response = activiteService.getComparaison("2026-05", null, 3);

        assertEquals("2026-05", response.getMois());
        assertEquals(120.0, response.getCas().getMoisCourant());
        assertEquals(127.0, response.getCas().getMoisPrecedent());
        assertEquals(127.33, response.getCas().getReferenceRecente());
        assertEquals(-7.0, response.getCas().getVariationVsMPrecedentVal());
        assertEquals(-5.5, response.getCas().getVariationVsMPrecedentPct());
        assertEquals(-7.33, response.getCas().getVariationVsRefVal());
        assertEquals(-5.8, response.getCas().getVariationVsRefPct());
    }

    @Test
    void kpisCalculateNonAffectesPercentageFromTotalCases() {
        Medecin identifiable = new Medecin();
        identifiable.setId(1L);
        identifiable.setNom("Martin");
        when(medecinRepository.findAllWithImportedDossiers()).thenReturn(List.of(identifiable));
        when(medecinRepository.isPseudoMedecin(identifiable)).thenReturn(false);
        when(extractionDonneesRepository.sumPrixAPayerByDateRange(any(), any())).thenReturn(79650L);
        when(extractionDonneesRepository.countCasByDateRange(any(), any())).thenReturn(120L);
        when(extractionDonneesRepository.countIdentifiedDoctorsByDateRange(any(), any(), any())).thenReturn(20L);
        when(extractionDonneesRepository.sumPrixAPayerForIdentifiedDoctorsByDateRange(any(), any(), any())).thenReturn(78650L);
        when(extractionDonneesRepository.countCasesForUnidentifiedDoctorsByDateRange(any(), any(), any())).thenReturn(12L);
        when(extractionDonneesRepository.sumPrixAPayerForUnidentifiedDoctorsByDateRange(any(), any(), any())).thenReturn(1000L);

        var response = activiteService.getKpisMensuels("2026-05");

        assertEquals(79650L, response.getCaMoisTotal());
        assertEquals(120L, response.getCasMoisTotal());
        assertEquals(10.0, response.getNonAffectesPct());
    }

    @Test
    void kpisCountPseudoDoctorRecordsAsUnidentified() {
        Medecin identifiable = new Medecin();
        identifiable.setId(1L);
        identifiable.setNom("Martin");
        Medecin nonPrecise = new Medecin();
        nonPrecise.setId(2L);
        nonPrecise.setNom("Non précisé");
        Medecin inconnu = new Medecin();
        inconnu.setId(3L);
        inconnu.setNom("Médecin inconnu");
        when(medecinRepository.findAllWithImportedDossiers()).thenReturn(List.of(identifiable, nonPrecise, inconnu));
        when(medecinRepository.isPseudoMedecin(identifiable)).thenReturn(false);
        when(medecinRepository.isPseudoMedecin(nonPrecise)).thenReturn(true);
        when(medecinRepository.isPseudoMedecin(inconnu)).thenReturn(true);
        when(extractionDonneesRepository.countCasByDateRange(any(), any())).thenReturn(174L);
        when(extractionDonneesRepository.sumPrixAPayerByDateRange(any(), any())).thenReturn(130530L);
        when(extractionDonneesRepository.sumPrixAPayerForIdentifiedDoctorsByDateRange(any(), any(), any())).thenReturn(127250L);
        when(extractionDonneesRepository.countCasesForUnidentifiedDoctorsByDateRange(any(), any(), any())).thenReturn(3L);
        when(extractionDonneesRepository.sumPrixAPayerForUnidentifiedDoctorsByDateRange(any(), any(), any())).thenReturn(3280L);
        when(extractionDonneesRepository.countIdentifiedDoctorsByDateRange(any(), any(), any())).thenReturn(1L);

        var response = activiteService.getKpisMensuels("2026-09");

        assertEquals(130530L, response.getCaMoisTotal());
        assertEquals(174L, response.getCasMoisTotal());
        assertEquals(127250L, response.getPortefeuilleCA());
        assertEquals(3L, response.getNonAffectesCount());
        assertEquals(3280L, response.getNonAffectesCA());
        verify(extractionDonneesRepository).countCasesForUnidentifiedDoctorsByDateRange(any(), any(), org.mockito.ArgumentMatchers.eq(List.of(1L)));
    }

    @Test
    void comparaisonUsesMetricFloorsForSmallReferences() {
        when(extractionDonneesRepository.countCasByDateRange(any(), any()))
                .thenAnswer(invocation -> casesForSmallReference(invocation.getArgument(0)));
        when(extractionDonneesRepository.sumPrixAPayerByDateRange(any(), any()))
                .thenAnswer(invocation -> caForSmallReference(invocation.getArgument(0)));

        var response = activiteService.getComparaison("2026-05", null, 3);

        assertEquals(33.0, response.getCa().getVariationVsRefPct());
        assertEquals(100.0, response.getCas().getVariationVsRefPct());
    }

    private Long casesFor(LocalDate start) {
        YearMonth month = YearMonth.from(start);
        return switch (month.toString()) {
            case "2026-05" -> 120L;
            case "2026-04" -> 127L;
            case "2026-03" -> 130L;
            case "2026-02" -> 125L;
            default -> 0L;
        };
    }

    private Long caFor(LocalDate start) {
        YearMonth month = YearMonth.from(start);
        return switch (month.toString()) {
            case "2026-05" -> 79650L;
            case "2026-04" -> 88960L;
            case "2026-03" -> 85000L;
            case "2026-02" -> 87000L;
            default -> 0L;
        };
    }

    private Long casesForSmallReference(LocalDate start) {
        return YearMonth.from(start).toString().equals("2026-05") ? 2L : 1L;
    }

    private Long caForSmallReference(LocalDate start) {
        return YearMonth.from(start).toString().equals("2026-05") ? 100L : 1L;
    }

    @Test
    void moisDisponiblesPrioritizesImportedLabDataOverTerrainVisits() {
        when(extractionDonneesRepository.findAllDatesDescending())
                .thenReturn(List.of(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 8, 15)));

        List<String> result = activiteService.getMoisDisponibles();

        assertEquals(List.of("2026-09", "2026-08"), result);
    }

    @Test
    void moisDisponiblesFallsBackToTerrainWhenNoLabData() {
        when(extractionDonneesRepository.findAllDatesDescending()).thenReturn(List.of());
        when(retourTerrainRepository.findAllDatesDescending())
                .thenReturn(List.of(LocalDate.of(2026, 10, 5)));

        List<String> result = activiteService.getMoisDisponibles();

        assertEquals(List.of("2026-10"), result);
    }
}
