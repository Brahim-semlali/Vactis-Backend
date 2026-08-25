package com.vactis.service;

import com.vactis.dto.medecin.RetourTerrainRequest;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.RetourTerrain;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.RetourTerrainRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetourTerrainServiceTest {

    @Mock private RetourTerrainRepository retourRepository;
    @Mock private MedecinRepository medecinRepository;

    @InjectMocks private RetourTerrainService retourService;

    @Test
    void addRetourTerrainAppliesDefaultsAndTrimsComment() {
        Medecin medecin = new Medecin();
        medecin.setNom("Martin");
        medecin.setPrenom("Alice");
        when(medecinRepository.findById(1L)).thenReturn(Optional.of(medecin));
        when(retourRepository.save(any(RetourTerrain.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RetourTerrainRequest request = new RetourTerrainRequest();
        request.setNote(4.0);
        request.setDateVisite(LocalDate.now().minusDays(1));
        request.setCommentaire(" visite ");
        RetourTerrain saved = retourService.addRetourTerrain(1L, request);

        assertEquals("Martin Alice", saved.getNomMedecin());
        assertEquals("visite", saved.getCommentaire());
        assertEquals("REALISEE", saved.getStatutVisite().name());
        assertEquals("NON_RENSEIGNE", saved.getQualification().name());
        verify(retourRepository).save(any(RetourTerrain.class));
    }

    @Test
    void addRetourTerrainRejectsFutureDate() {
        Medecin medecin = new Medecin();
        when(medecinRepository.findById(1L)).thenReturn(Optional.of(medecin));
        RetourTerrainRequest request = new RetourTerrainRequest();
        request.setNote(4.0);
        request.setDateVisite(LocalDate.now().plusDays(1));

        assertThrows(ResponseStatusException.class, () -> retourService.addRetourTerrain(1L, request));
        verify(retourRepository, never()).save(any());
    }
}
