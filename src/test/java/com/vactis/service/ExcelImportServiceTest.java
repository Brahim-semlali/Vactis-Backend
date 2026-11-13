package com.vactis.service;

import com.vactis.model.Controle.TypeControle;
import com.vactis.model.action.Action;
import com.vactis.model.data.ExtractionDonnees;
import com.vactis.model.medecin.Medecin;
import com.vactis.repository.AlerteHebdomadaireRepository;
import com.vactis.repository.ActionRepository;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.ReclamationHistoriqueRepository;
import com.vactis.repository.ReclamationRepository;
import com.vactis.repository.RetourTerrainRepository;
import com.vactis.repository.notification.NotificationRepository;
import com.vactis.service.Activite.SegmentationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExcelImportServiceTest {

    @Mock
    private ControleService controleService;

    @Mock
    private SegmentationService segmentationService;

    @Mock
    private ExtractionDonneesRepository extractionDonneesRepository;

    @Mock
    private MedecinRepository medecinRepository;

    @Mock
    private ActionRepository actionRepository;

    @Mock
    private RetourTerrainRepository retourTerrainRepository;

    @Mock
    private AlerteHebdomadaireRepository alerteHebdomadaireRepository;

    @Mock
    private ReclamationHistoriqueRepository reclamationHistoriqueRepository;

    @Mock
    private ReclamationRepository reclamationRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private org.springframework.core.io.ResourceLoader resourceLoader;

    @InjectMocks
    private ExcelImportService excelImportService;

    @Test
    void remplaceLesAnciennesDonneesEtImporteLeClasseurComplet() throws Exception {
        when(medecinRepository.save(any(Medecin.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(controleService.determinerEtat(any(TypeControle.class), anyLong()))
                .thenReturn("ACTIF_STABLE");

        try (InputStream input = getClass().getResourceAsStream("/data/data_fictif_test_vactis.xlsx")) {
            if (input == null) throw new AssertionError("Classeur de données fictives introuvable");
            excelImportService.importFromExcelStream(input);
        }

        verify(medecinRepository, times(27)).save(any(Medecin.class));
        verify(medecinRepository).deleteAllInBatch();
        verify(medecinRepository).flush();
        verify(reclamationHistoriqueRepository).deleteAllInBatch();
        verify(reclamationRepository).deleteAllInBatch();
        verify(alerteHebdomadaireRepository).deleteAllInBatch();
        verify(retourTerrainRepository).deleteAllInBatch();
        verify(actionRepository).deleteAllInBatch();
        verify(extractionDonneesRepository).deleteAllInBatch();
        verify(notificationRepository).deleteByType("RECLAMATION");
        verify(segmentationService).recalculerSegmentationPortefeuille(LocalDate.of(2026, 9, 30));

        ArgumentCaptor<Iterable<ExtractionDonnees>> dossiersCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(extractionDonneesRepository).saveAll(dossiersCaptor.capture());
        List<ExtractionDonnees> importedDossiers = StreamSupport.stream(
            dossiersCaptor.getValue().spliterator(), false).toList();
        assertEquals(2569, importedDossiers.size());
        assertEquals(13, importedDossiers.stream().filter(dossier -> dossier.getMedecin() == null).count());

        ArgumentCaptor<Iterable<Action>> actionsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(actionRepository).saveAll(actionsCaptor.capture());
        List<Action> actions = new ArrayList<>();
        actionsCaptor.getValue().forEach(actions::add);
        assertEquals(27, actions.size());
        assertEquals("2026-09", actions.get(0).getCycleMensuel());
    }
}