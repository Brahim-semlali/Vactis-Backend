package com.vactis.service.Activite;

import com.vactis.model.Controle.TypeControle;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.RetourTerrain;
import com.vactis.model.medecin.StatutVisite;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.RetourTerrainRepository;
import com.vactis.service.ControleService;
import com.vactis.service.RetourTerrainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Segmentation du portefeuille : potentiel (note terrain > note saisie > note neutre), rang de performance CA,
 * poids économique, score de valeur 40/40/20 et segment A/B/C/D (table de contrôle prioritaire).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SegmentationServiceTest {

    private static final LocalDate REFERENCE = LocalDate.of(2026, 10, 31);

    @Mock private MedecinRepository medecinRepository;
    @Mock private ExtractionDonneesRepository extractionDonneesRepository;
    @Mock private RetourTerrainRepository retourTerrainRepository;
    @Mock private ControleService controleService;
    @Mock private RetourTerrainService retourTerrainService;

    @InjectMocks private SegmentationService service;

    private Medecin petit;
    private Medecin moyen;
    private Medecin grand;

    @BeforeEach
    void setUp() {
        petit = medecin(1L);
        moyen = medecin(2L);
        moyen.setNoteInput(5.0);
        grand = medecin(3L);
        grand.setNoteInput(1.0);

        RetourTerrain visite = new RetourTerrain();
        visite.setStatutVisite(StatutVisite.REALISEE);
        visite.setNote(4.0);
        when(retourTerrainService.getDerniereVisite(grand)).thenReturn(Optional.of(visite));
        when(retourTerrainService.getDerniereVisite(petit)).thenReturn(Optional.empty());
        when(retourTerrainService.getDerniereVisite(moyen)).thenReturn(Optional.empty());

        when(medecinRepository.findAllWithImportedDossiers()).thenReturn(List.of(grand, petit, moyen));
        when(extractionDonneesRepository.sumCaGroupedByMedecin()).thenReturn(List.of(
                new Object[]{1L, 1000L}, new Object[]{2L, 5000L}, new Object[]{3L, 10000L}));
        when(extractionDonneesRepository.countCasGroupedByMedecin()).thenReturn(List.of(
                new Object[]{1L, 10L}, new Object[]{2L, 50L}, new Object[]{3L, 100L}));
    }

    private static Medecin medecin(Long id) {
        Medecin m = new Medecin();
        m.setId(id);
        m.setNom("M" + id);
        m.setStatut("ACTIF");
        return m;
    }

    @Test
    void scoreDeValeurEtSegmentParMedecin() {
        service.recalculerSegmentationPortefeuille(REFERENCE);

        // Rang de performance sur le CA historique : du plus petit (0) au plus grand (100)
        assertEquals(1, petit.getRangPerformance());
        assertEquals(0.0, petit.getPerformanceSur100());
        assertEquals(50.0, moyen.getPerformanceSur100());
        assertEquals(100.0, grand.getPerformanceSur100());
        assertEquals(3, grand.getTotalPortefeuillePerformance());

        // Potentiel : note terrain prioritaire sur la note saisie, sinon note neutre 3/5
        assertEquals("DEFAUT", petit.getSourcePotentiel());
        assertEquals(60.0, petit.getPotentielSur100());
        assertEquals("INPUT_PROFIL", moyen.getSourcePotentiel());
        assertEquals(100.0, moyen.getPotentielSur100());
        assertEquals("NOTE_TERRAIN", grand.getSourcePotentiel());
        assertEquals(80.0, grand.getPotentielSur100());

        // Score = 0,40 potentiel + 0,40 performance + 0,20 poids économique
        assertEquals(26.0, petit.getScoreValeur());
        assertEquals(70.0, moyen.getScoreValeur());
        assertEquals(92.0, grand.getScoreValeur());
        assertEquals("D", petit.getSegment());
        assertEquals("B", moyen.getSegment());
        assertEquals("A", grand.getSegment());

        // Sans dossier connu, les indicateurs du mois restent vides
        assertNull(petit.getCaMoisAnalyse());
        assertNull(petit.getJoursSansActivite());
        assertEquals("NON_FIABLE", petit.getFiabilite());
        verify(medecinRepository).saveAll(any());
    }

    @Test
    void laTableDeControleDesSegmentsEstPrioritaire() {
        when(controleService.determinerEtatParScore(eq(TypeControle.SEGEMENTS), anyDouble())).thenReturn("C");

        service.recalculerSegmentationPortefeuille(REFERENCE);

        assertEquals("C", grand.getSegment());
        assertEquals("C", petit.getSegment());
    }

    @Test
    void lesPseudoMedecinsSontExclusDuPortefeuille() {
        when(medecinRepository.isPseudoMedecin(any())).thenReturn(true);

        service.recalculerSegmentationPortefeuille(REFERENCE);

        verify(medecinRepository, never()).saveAll(any());
        assertNull(grand.getSegment());
    }

    @Test
    void laReferenceEstLaFinDuDernierMoisAvecDonnees() {
        when(extractionDonneesRepository.findAllDatesDescending()).thenReturn(List.of(LocalDate.of(2026, 9, 14)));

        service.recalculerSegmentationPortefeuille();

        assertEquals(LocalDate.of(2026, 9, 30), grand.getDateReferenceIndicateurs());
    }
}
