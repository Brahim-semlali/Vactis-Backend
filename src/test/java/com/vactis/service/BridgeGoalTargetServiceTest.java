package com.vactis.service;

import com.vactis.dto.bridgetogoal.BridgeGoalTargetRequest;
import com.vactis.dto.bridgetogoal.BridgeGoalTargetResponse;
import com.vactis.model.system.BridgeGoalTarget;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.system.BridgeGoalTargetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BridgeGoalTargetServiceTest {
    @Mock
    private BridgeGoalTargetRepository targetRepository;

    @Mock
    private ExtractionDonneesRepository extractionDonneesRepository;

    @InjectMocks
    private BridgeGoalTargetService service;

    @Test
    void calculatesPercentageFromActualPreviousCalendarMonth() {
        when(extractionDonneesRepository.sumPrixAPayerByDateRange(any(), any())).thenReturn(1_500_000L);
        when(targetRepository.findByPeriod("2026-09")).thenReturn(Optional.empty());
        when(targetRepository.save(any(BridgeGoalTarget.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BridgeGoalTargetResponse response = service.save(
            new BridgeGoalTargetRequest("2026-09", "PERCENTAGE", BigDecimal.valueOf(20)));

        assertEquals(1_500_000L, response.previousMonthRevenue());
        assertEquals(1_800_000L, response.targetAmount());
        assertEquals("2026-09", response.mois());
        verify(extractionDonneesRepository).sumPrixAPayerByDateRange(
            eq(LocalDate.of(2026, 8, 1)), eq(LocalDate.of(2026, 8, 31)));
    }

    @Test
    void savesDirectMonthlyTargetWithoutApplyingPercentage() {
        when(extractionDonneesRepository.sumPrixAPayerByDateRange(any(), any())).thenReturn(1_500_000L);
        when(targetRepository.findByPeriod("2026-09")).thenReturn(Optional.empty());
        when(targetRepository.save(any(BridgeGoalTarget.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BridgeGoalTargetResponse response = service.save(
            new BridgeGoalTargetRequest("2026-09", "DIRECT", BigDecimal.valueOf(2_000_000)));

        assertEquals("DIRECT", response.method());
        assertEquals(BigDecimal.valueOf(2_000_000), response.value());
        assertEquals(2_000_000L, response.targetAmount());
        assertEquals("2026-09", response.mois());
    }

    @Test
    void usesLatestImportedDataMonthWhenPeriodIsNull() {
        when(extractionDonneesRepository.findAllDatesDescending())
            .thenReturn(java.util.List.of(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 8, 10)));
        when(extractionDonneesRepository.sumPrixAPayerByDateRange(any(), any())).thenReturn(1_200_000L);
        when(targetRepository.findByPeriod("2026-09")).thenReturn(Optional.empty());

        BridgeGoalTargetResponse response = service.get(null);

        assertEquals("2026-09", response.mois());
        assertEquals(1_200_000L, response.previousMonthRevenue());
    }
}