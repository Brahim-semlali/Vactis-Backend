package com.vactis.service.Activite;

import com.vactis.repository.ActionRepository;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.RetourTerrainRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActiviteImpactServiceTest {

    @Mock private RetourTerrainRepository retourRepository;
    @Mock private ActionRepository actionRepository;
    @Mock private MedecinRepository medecinRepository;
    @Mock private ExtractionDonneesRepository extractionRepository;
    @Mock private ActivitePortefeuilleService portefeuilleService;

    @InjectMocks private ActiviteImpactService service;

    @Test
    void rapportImpactReturnsZeroCountersWhenNoTerrainDataExists() {
        when(retourRepository.findByDateVisiteBetweenWithFetch(any(), any())).thenReturn(List.of());
        when(actionRepository.countByCycleMensuel("2026-05")).thenReturn(0L);
        when(actionRepository.countActionsExcluesDirectionByCycle("2026-05")).thenReturn(0L);

        var response = service.getRapportImpact("2026-05");

        assertEquals("2026-05", response.getMois());
        assertEquals(0L, response.getTotalVisitesRenseignees());
        assertEquals(0L, response.getActionsVactisGenerees());
        assertEquals(0.0, response.getTauxRealisation());
    }
}
