package com.vactis.service.Activite;

import com.vactis.repository.ActionRepository;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.RetourTerrainRepository;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.QualificationVisite;
import com.vactis.model.medecin.RetourTerrain;
import com.vactis.dto.activite.ConcordanceQualificationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void allQualificationTypesAreReportedNonObservableWhenFollowingMonthIsNotClosed() {
        YearMonth mois = YearMonth.now();
        List<RetourTerrain> retours = new ArrayList<>();
        QualificationVisite[] qualifications = QualificationVisite.values();
        for (int index = 0; index < qualifications.length; index++) {
            Medecin medecin = new Medecin();
            medecin.setId((long) index + 1);
            medecin.setNom("Médecin " + index);
            medecin.setPrenom("");
            RetourTerrain retour = new RetourTerrain();
            retour.setId((long) index + 10);
            retour.setMedecin(medecin);
            retour.setDateVisite(mois.atDay(10));
            retour.setQualification(qualifications[index]);
            retours.add(retour);
        }

        when(retourRepository.findByDateVisiteBetweenWithFetch(mois.atDay(1), mois.atEndOfMonth())).thenReturn(retours);
        when(actionRepository.findAll()).thenReturn(List.of());
        List<Medecin> medecins = retours.stream().map(RetourTerrain::getMedecin).toList();
        when(medecinRepository.findAllWithImportedDossiers()).thenReturn(medecins);
        when(portefeuilleService.buildStatutMapForMonth(mois, medecins)).thenReturn(Map.of());
        when(portefeuilleService.buildStatutMapForMonth(mois.plusMonths(1), medecins)).thenReturn(Map.of());
        when(extractionRepository.findMedecinIdsWithActivityInRange(
                mois.plusMonths(1).atDay(1), mois.plusMonths(1).atEndOfMonth())).thenReturn(List.of());
        when(extractionRepository.findAllDatesDescending()).thenReturn(List.of(LocalDate.now()));

        ConcordanceQualificationResponse response = service.getConcordanceQualification(mois.toString());

        assertEquals(qualifications.length, response.getTotalVisitesQualifiees());
        assertEquals(qualifications.length, response.getNonObservables());
        assertTrue(response.getDetails().stream().allMatch(item -> "NON_OBSERVABLE".equals(item.getEvolutionObservee())));
    }
}
