package com.vactis.service.Activite;

import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.RetourTerrainRepository;
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
        when(extractionDonneesRepository.sumPrixAPayerByDateRange(any(), any())).thenReturn(79650L);
        when(extractionDonneesRepository.countCasByDateRange(any(), any())).thenReturn(120L);
        when(extractionDonneesRepository.countMedecinsDistinctsByDateRange(any(), any())).thenReturn(20L);
        when(extractionDonneesRepository.sumPrixAPayerWithMedecinByDateRange(any(), any())).thenReturn(79650L);
        when(extractionDonneesRepository.countNonAffectesByDateRange(any(), any())).thenReturn(12L);
        when(extractionDonneesRepository.sumPrixAPayerNonAffectesByDateRange(any(), any())).thenReturn(1000L);
        when(medecinRepository.count()).thenReturn(22L);

        var response = activiteService.getKpisMensuels("2026-05");

        assertEquals(79650L, response.getCaMoisTotal());
        assertEquals(120L, response.getCasMoisTotal());
        assertEquals(10.0, response.getNonAffectesPct());
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
}
