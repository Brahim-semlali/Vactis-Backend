package com.vactis.service;

import com.vactis.model.action.EtatAction;
import com.vactis.repository.ActionRepository;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.RetourTerrainRepository;
import com.vactis.service.Activite.SegmentationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionServiceTest {

    @Mock private ActionRepository actionRepository;
    @Mock private ControleService controleService;
    @Mock private MedecinService medecinService;
    @Mock private RetourTerrainRepository retourTerrainRepository;
    @Mock private MedecinRepository medecinRepository;
    @Mock private SegmentationService segmentationService;
    @Mock private ExtractionDonneesRepository extractionDonneesRepository;
    @Mock private RetourTerrainService retourTerrainService;

    @InjectMocks private ActionService actionService;

    @Test
    void countPlanifieesDelegatesToActionRepository() {
        when(actionRepository.countByEtatAction(EtatAction.PLANIFIEE)).thenReturn(7L);

        assertEquals(7L, actionService.countPlanifiees());
    }

    @Test
    void kpisExposeRepositoryCounts() {
        when(actionRepository.countAllActions()).thenReturn(20L);
        when(actionRepository.countByEtatAction(EtatAction.PLANIFIEE)).thenReturn(12L);
        when(actionRepository.countByEtatAction(EtatAction.REALISEE)).thenReturn(8L);
        when(actionRepository.countByBacklogTrue()).thenReturn(3L);
        when(actionRepository.countByUrgenceSilenceTrue()).thenReturn(2L);

        var kpis = actionService.getKpis();

        assertEquals(20L, kpis.getActionsGenerees());
        assertEquals(12L, kpis.getPlanifiees());
        assertEquals(8L, kpis.getVisites());
        assertEquals(3L, kpis.getBacklog());
        assertEquals(2L, kpis.getUrgenceSilence());
    }
}
