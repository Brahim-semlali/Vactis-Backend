package com.vactis.service.Activite;

import com.vactis.model.medecin.Medecin;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SegmentationRankingTest {

    @Test
    void breaksEqualAverageRevenueByStableDoctorId() {
        Medecin higherId = new Medecin();
        higherId.setId(22L);
        Medecin lowerId = new Medecin();
        lowerId.setId(21L);

        List<Medecin> ordered = List.of(higherId, lowerId).stream()
                .sorted(SegmentationService.ordrePerformanceParCa(Map.of(21L, 6500.0, 22L, 6500.0)))
                .toList();

        assertEquals(List.of(21L, 22L), ordered.stream().map(Medecin::getId).toList());
    }

    @Test
    void historicalSilenceUsesOnlyActivityDatesOnOrBeforeReferenceDate() {
        LocalDate reference = LocalDate.of(2026, 5, 31);
        List<LocalDate> allDates = List.of(
                LocalDate.of(2026, 6, 15),
                LocalDate.of(2026, 5, 27),
                LocalDate.of(2026, 5, 23),
                LocalDate.of(2026, 5, 18));

        List<LocalDate> datesAtReference = SegmentationService.datesJusquaReference(allDates, reference);

        assertEquals(List.of(LocalDate.of(2026, 5, 27), LocalDate.of(2026, 5, 23),
                LocalDate.of(2026, 5, 18)), datesAtReference);
        assertEquals(5, SegmentationService.calculateIntervalleEffectif(datesAtReference));
        assertEquals(4, SegmentationService.calculateDaysSinceLastActivity(datesAtReference, reference));
    }
}