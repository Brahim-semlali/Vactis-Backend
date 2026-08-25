package com.vactis.service;

import com.vactis.model.medecin.Medecin;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import com.vactis.service.Activite.SegmentationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MedecinServiceTest {

    @Mock private MedecinRepository medecinRepository;
    @Mock private ActionService actionService;
    @Mock private ExcelImportService excelImportService;
    @Mock private ControleService controleService;
    @Mock private SegmentationService segmentationService;
    @Mock private ExtractionDonneesRepository extractionDonneesRepository;

    @InjectMocks private MedecinService medecinService;

    @Test
    void updateNoteInputAcceptsValueBetweenOneAndFive() {
        Medecin medecin = new Medecin();
        when(medecinRepository.findById(1L)).thenReturn(Optional.of(medecin));
        when(medecinRepository.save(medecin)).thenReturn(medecin);

        Medecin result = medecinService.updateNoteInput(1L, 4.5);

        assertEquals(4.5, result.getNoteInput());
        verify(medecinRepository).save(medecin);
    }

    @Test
    void updateNoteInputRejectsValueOutsideRange() {
        Medecin medecin = new Medecin();
        when(medecinRepository.findById(1L)).thenReturn(Optional.of(medecin));

        assertThrows(ResponseStatusException.class, () -> medecinService.updateNoteInput(1L, 6.0));
        verify(medecinRepository, never()).save(any());
    }

    @Test
    void findByCodeNormalizesWhitespaceAndCase() {
        Medecin medecin = new Medecin();
        when(medecinRepository.findByCodeMedecinIgnoreCase("med001")).thenReturn(Optional.of(medecin));

        assertEquals(medecin, medecinService.findByCodeMedecin(" med001 "));
    }
}
