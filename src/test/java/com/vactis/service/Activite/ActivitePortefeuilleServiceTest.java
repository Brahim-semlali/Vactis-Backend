package com.vactis.service.Activite;

import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivitePortefeuilleServiceTest {

    @Mock private ExtractionDonneesRepository extractionRepository;
    @Mock private MedecinRepository medecinRepository;

    @InjectMocks private ActivitePortefeuilleService service;

    @Test
    void emptyPortfolioProducesAnEmptyStatusMap() {
        when(extractionRepository.sumCaByMedecinAndDateRange(any(), any())).thenReturn(List.of());
        when(extractionRepository.findMedecinIdsWithActivityInRange(any(), any())).thenReturn(List.of());

        assertTrue(service.buildStatutMapForMonth(YearMonth.of(2026, 5), List.of()).isEmpty());
    }
}
