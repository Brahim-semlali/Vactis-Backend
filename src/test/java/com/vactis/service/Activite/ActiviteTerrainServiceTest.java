package com.vactis.service.Activite;

import com.vactis.model.medecin.QualificationVisite;
import com.vactis.model.medecin.RetourTerrain;
import com.vactis.model.medecin.StatutVisite;
import com.vactis.repository.ActionRepository;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.RetourTerrainRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActiviteTerrainServiceTest {

    @Mock private RetourTerrainRepository retourTerrainRepository;
    @Mock private ActionRepository actionRepository;
    @Mock private ExtractionDonneesRepository extractionDonneesRepository;

    @InjectMocks private ActiviteTerrainService terrainService;

    @Test
    void compteRenduCalculeLesStatutsQualificationsEtTaux() {
        RetourTerrain realisee = retour(StatutVisite.REALISEE, QualificationVisite.FAVORABLE, true, "Alice");
        RetourTerrain nonRealisee = retour(StatutVisite.NON_REALISEE, QualificationVisite.DEFAVORABLE, false, "Alice");
        when(retourTerrainRepository.findByDateVisiteBetween(any(), any())).thenReturn(List.of(realisee, nonRealisee));

        var response = terrainService.getCompteRenduTerrain("2026-05");

        assertEquals(2L, response.getVisitesRenseignees());
        assertEquals(1L, response.getVisitesRealisees());
        assertEquals(1L, response.getVisitesNonRealisees());
        assertEquals(50.0, response.getTauxTerrain());
        assertEquals(1L, response.getVisitesAvecReclamation());
        assertEquals(1L, response.getFavorables());
        assertEquals(1L, response.getDefavorablesRefus());
    }

    @Test
    void statutNullEstTraiteCommeRealiseePourLesDonneesHistoriques() {
        RetourTerrain historique = retour(null, QualificationVisite.NON_RENSEIGNE, false, null);
        when(retourTerrainRepository.findByDateVisiteBetween(any(), any())).thenReturn(List.of(historique));

        var response = terrainService.getActionsVactis("2026-05");

        assertEquals(1L, response.getVisitesRenseignees());
        assertEquals(1L, response.getVisitesRealisees());
        assertEquals(100.0, response.getTauxTerrain());
    }

    private RetourTerrain retour(StatutVisite statut, QualificationVisite qualification, boolean reclamation, String visiteur) {
        RetourTerrain retour = new RetourTerrain();
        retour.setDateVisite(LocalDate.of(2026, 5, 12));
        retour.setStatutVisite(statut);
        retour.setQualification(qualification);
        retour.setReclamation(reclamation);
        retour.setVisiteur(visiteur);
        return retour;
    }
}
