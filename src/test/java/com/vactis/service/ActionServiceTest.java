package com.vactis.service;

import com.vactis.model.action.EtatAction;
import com.vactis.model.action.Action;
import com.vactis.model.action.StatutPlanRetention;
import com.vactis.model.action.TypeEtapeRetention;
import com.vactis.model.action.UrgenceAction;
import com.vactis.dto.action.SaisieRetourTerrainRequest;
import com.vactis.dto.action.SaisieVisiteLibreRequest;
import com.vactis.model.medecin.ObstaclePrincipal;
import com.vactis.model.medecin.QualificationVisite;
import com.vactis.model.medecin.TypeVisite;
import com.vactis.dto.reclamation.ReclamationRequest;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.RetourTerrain;
import com.vactis.model.reclamation.CategorieReclamation;
import com.vactis.model.reclamation.PrioriteReclamation;
import com.vactis.model.reclamation.Reclamation;
import com.vactis.repository.ActionRepository;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.RetourTerrainRepository;
import com.vactis.service.Activite.SegmentationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    @Mock private ReclamationService reclamationService;

    @InjectMocks private ActionService actionService;

    @Test
    void countPlanifieesCountsOnlyEligibleActions() {
        List<Action> actions = new ArrayList<>();
        for (int index = 0; index < 7; index++) actions.add(action(EtatAction.PLANIFIEE, false, false));
        when(actionRepository.findAllForImportedDoctors()).thenReturn(actions);
        when(medecinRepository.isPseudoMedecin(org.mockito.ArgumentMatchers.any(Medecin.class))).thenReturn(false);

        assertEquals(7L, actionService.countPlanifiees());
    }

    @Test
    void kpisCountOnlyEligibleActions() {
        List<Action> actions = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            actions.add(action(index < 12 ? EtatAction.PLANIFIEE : EtatAction.REALISEE,
                    index < 3, index < 2));
        }
        when(actionRepository.findAllForImportedDoctors()).thenReturn(actions);
        when(medecinRepository.isPseudoMedecin(org.mockito.ArgumentMatchers.any(Medecin.class))).thenReturn(false);

        var kpis = actionService.getKpis();

        assertEquals(20L, kpis.getActionsGenerees());
        assertEquals(12L, kpis.getPlanifiees());
        assertEquals(8L, kpis.getVisites());
        assertEquals(3L, kpis.getBacklog());
        assertEquals(2L, kpis.getUrgenceSilence());
    }

    @Test
    void syncActionsUsesIntensityCategoryForVisitHorizons() {
        List<Action> actions = new ArrayList<>();
        double[] urgencyScores = {10.0, 40.0, 60.0, 70.0, 100.0};
        double[] intensities = {10.0, 40.0, 50.0, 75.0, 100.0};
        LocalDate dateReference = LocalDate.of(2026, 9, 30);
        for (int index = 0; index < urgencyScores.length; index++) {
            Medecin medecin = new Medecin();
            medecin.setId((long) index + 1);
            medecin.setNom("Medecin " + index);
            medecin.setStatut("PROGRESSION");
            medecin.setSegment("C");
            medecin.setScoreUrgence(urgencyScores[index]);
            medecin.setIntensiteRisque(intensities[index]);
            medecin.setNiveauIntensite(index == 4 ? "CRITIQUE" : index >= 2 ? "ELEVEE" : "NORMALE");

            Action action = new Action();
            action.setMedecin(medecin);
            action.setStatut("PROGRESSION");
            action.setSegment("C");
            action.setActionRecommandee("visite suivi progression");
            action.setUrgence(com.vactis.model.action.UrgenceAction.FAIBLE);
            action.setUrgenceSilence(false);
            action.setEtatAction(EtatAction.PLANIFIEE);
            action.setHorizonJours(30);
            action.setDateVisite(dateReference.plusDays(7));
            actions.add(action);
        }
        when(actionRepository.findAll()).thenReturn(actions);
        when(medecinRepository.isPseudoMedecin(org.mockito.ArgumentMatchers.any(Medecin.class))).thenReturn(false);
        when(extractionDonneesRepository.findAllDatesDescending()).thenReturn(List.of(LocalDate.of(2026, 9, 15)));

        actionService.syncActionsWithMedecins();

        assertEquals(List.of(30, 30, 15, 15, 7),
                actions.stream().map(Action::getHorizonJours).toList());
        assertEquals(List.of(7, 7, 15, 15, 7),
            actions.stream().map(action -> java.time.temporal.ChronoUnit.DAYS.between(
                dateReference, action.getDateVisite())).map(Long::intValue).toList());
    }

    @Test
    void syncActionsRemovesDuplicateUntouchedImportGeneratedActions() {
        Medecin medecin = new Medecin();
        medecin.setId(42L);
        medecin.setNom("Rahmani");
        medecin.setPrenom("Youssef");
        medecin.setStatut("ACTIF_STABLE");
        medecin.setSegment("B");

        Action first = importedAction(medecin);
        Action duplicate = importedAction(medecin);
        first.setActionRecommandee("visite_diagnostic");
        duplicate.setActionRecommandee("visite diagnostic");
        duplicate.setSourceRecommandation(null);
        duplicate.setCycleMensuel(null);
        first.setTypeEtapeRetention(TypeEtapeRetention.ACTION_1);
        first.setStatutPlanRetention(StatutPlanRetention.EN_COURS);
        duplicate.setTypeEtapeRetention(TypeEtapeRetention.ACTION_1);
        duplicate.setStatutPlanRetention(StatutPlanRetention.EN_COURS);
        List<Action> actions = new ArrayList<>(List.of(first, duplicate));
        when(actionRepository.findAll()).thenReturn(actions);
        when(medecinRepository.isPseudoMedecin(medecin)).thenReturn(false);
        when(medecinRepository.findAllWithImportedDossiers()).thenReturn(List.of(medecin));
        when(extractionDonneesRepository.findAllDatesDescending()).thenReturn(List.of(LocalDate.of(2026, 9, 30)));

        actionService.syncActionsWithMedecins();

        assertEquals(List.of(first), actions);
        verify(actionRepository).deleteAll(List.of(duplicate));
    }

    @Test
    void syncActionsKeepsCompletedActionAndDeletesItsPlannedAutomaticDuplicate() {
        Medecin medecin = new Medecin();
        medecin.setId(43L);
        medecin.setNom("Rahmani");
        medecin.setPrenom("Youssef");
        medecin.setStatut("RETENTION");
        medecin.setSegment("B");

        Action completed = importedAction(medecin);
        completed.setEtatAction(EtatAction.REALISEE);
        completed.setActionRecommandee("visite_diagnostic");
        completed.setTypeEtapeRetention(TypeEtapeRetention.ACTION_1);
        completed.setStatutPlanRetention(StatutPlanRetention.EN_COURS);

        Action plannedDuplicate = importedAction(medecin);
        plannedDuplicate.setActionRecommandee("visite diagnostic");
        plannedDuplicate.setTypeEtapeRetention(TypeEtapeRetention.ACTION_1);
        plannedDuplicate.setStatutPlanRetention(StatutPlanRetention.EN_COURS);

        List<Action> actions = new ArrayList<>(List.of(completed, plannedDuplicate));
        when(actionRepository.findAll()).thenReturn(actions);
        when(medecinRepository.isPseudoMedecin(medecin)).thenReturn(false);
        when(medecinRepository.findAllWithImportedDossiers()).thenReturn(List.of(medecin));
        when(extractionDonneesRepository.findAllDatesDescending()).thenReturn(List.of(LocalDate.of(2026, 9, 30)));

        actionService.syncActionsWithMedecins();

        assertEquals(List.of(completed), actions);
        verify(actionRepository).deleteAll(List.of(plannedDuplicate));
    }

    private Action action(EtatAction state, boolean backlog, boolean silence) {
        Medecin medecin = new Medecin();
        medecin.setNom("Oulad");
        medecin.setPrenom("Badr");
        Action action = new Action();
        action.setMedecin(medecin);
        action.setEtatAction(state);
        action.setBacklog(backlog);
        action.setUrgenceSilence(silence);
        return action;
    }

    private Action importedAction(Medecin medecin) {
        Action action = action(EtatAction.PLANIFIEE, false, false);
        action.setMedecin(medecin);
        action.setStatut("ACTIF_STABLE");
        action.setSegment("B");
        action.setActionRecommandee("visite_diagnostic");
        action.setUrgence(UrgenceAction.FAIBLE);
        action.setSourceRecommandation("IMPORT_AUTO");
        action.setCycleMensuel("2026-09");
        return action;
    }

        @Test
        void validateVisitRequestAcceptsOnlyDocumentedNonRealizationMotifs() {
        actionService.validateVisitRequest(false, LocalDate.now(), "MANQUE_DE_TEMPS",
            null, null, null, null, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> actionService.validateVisitRequest(
            false, LocalDate.now(), "CONTRAINTE_PLANNING", null, null, null, null, null, null, null, null));
        }

        @Test
        void validateVisitRequestRequiresCommentAndObstacleForDefavorableResult() {
        assertThrows(IllegalArgumentException.class, () -> actionService.validateVisitRequest(
            true, LocalDate.now(), null, "DEFAVORABLE", "", null, null, "PRIX_TARIF", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> actionService.validateVisitRequest(
            true, LocalDate.now(), null, "DEFAVORABLE", "Prix trop élevé", null, null, "QUALITE_DELAI", null, null, null));
        actionService.validateVisitRequest(true, LocalDate.now(), null, "DEFAVORABLE",
            "Prix trop élevé", null, null, "PRIX_TARIF", null, null, null);
        }

        @Test
        void validateVisitRequestRequiresReviewWhenLeaveReturnDateIsUnknown() {
        assertThrows(IllegalArgumentException.class, () -> actionService.validateVisitRequest(
            true, LocalDate.now(), null, "CONGE_ABSENCE", null, null, null, null,
            LocalDate.now(), null, null));
        actionService.validateVisitRequest(true, LocalDate.now(), null, "CONGE_ABSENCE", null,
            null, null, null, LocalDate.now(), null, LocalDate.now().plusDays(30));
        }

    @Test
    void failedComplaintTicketPreventsActionSubmission() {
        Medecin medecin = new Medecin();
        medecin.setId(5L);
        Action action = new Action();
        action.setMedecin(medecin);
        when(actionRepository.findById(7L)).thenReturn(Optional.of(action));
        when(retourTerrainRepository.save(any(RetourTerrain.class))).thenAnswer(invocation -> {
            RetourTerrain retour = invocation.getArgument(0);
            retour.setId(8L);
            return retour;
        });
        when(reclamationService.creerReclamation(any(ReclamationRequest.class), any(String.class)))
                .thenThrow(new IllegalStateException("notification failed"));

        SaisieRetourTerrainRequest request = new SaisieRetourTerrainRequest();
        request.setActionRealisee(true);
        request.setDateVisite(LocalDate.now());
        request.setQualification("RECLAMATION");
        request.setCommentaire("Incident signalé");

        assertThrows(IllegalStateException.class,
                () -> actionService.soumettreRetourTerrain(7L, request, "commercial"));
        verify(actionRepository, never()).save(any(Action.class));
    }

    @Test
    void complaintQualificationCreatesLinkedClaimForReclamationsModule() {
        Medecin medecin = new Medecin();
        medecin.setId(5L);
        Action action = new Action();
        action.setMedecin(medecin);
        LocalDate visitDate = LocalDate.now();
        when(actionRepository.findById(7L)).thenReturn(Optional.of(action));
        when(retourTerrainRepository.save(any(RetourTerrain.class))).thenAnswer(invocation -> {
            RetourTerrain retour = invocation.getArgument(0);
            retour.setId(8L);
            return retour;
        });
        Reclamation createdClaim = new Reclamation();
        createdClaim.setId(91L);
        when(reclamationService.creerReclamation(any(ReclamationRequest.class), any(String.class)))
                .thenReturn(createdClaim);
        when(actionRepository.save(any(Action.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SaisieRetourTerrainRequest request = new SaisieRetourTerrainRequest();
        request.setActionRealisee(true);
        request.setDateVisite(visitDate);
        request.setQualification("RECLAMATION");
        request.setCommentaire("Résultat reçu incomplet");

        Action savedAction = actionService.soumettreRetourTerrain(7L, request, "commercial");

        ArgumentCaptor<ReclamationRequest> claimRequest = ArgumentCaptor.forClass(ReclamationRequest.class);
        verify(reclamationService).creerReclamation(claimRequest.capture(), org.mockito.ArgumentMatchers.eq("commercial"));
        assertEquals(5L, claimRequest.getValue().getMedecinId());
        assertEquals(visitDate, claimRequest.getValue().getDateVisite());
        assertEquals("Résultat reçu incomplet", claimRequest.getValue().getDescription());
        assertEquals(CategorieReclamation.AUTRE, claimRequest.getValue().getCategorie());
        assertEquals(PrioriteReclamation.HAUTE, claimRequest.getValue().getPriorite());
        assertEquals(8L, claimRequest.getValue().getRetourTerrainId());
        assertEquals(91L, savedAction.getReclamationId());
        verify(retourTerrainRepository).save(org.mockito.ArgumentMatchers.argThat(retour -> retour.getNote() == null));
    }

    @Test
    void passageDeServiceHorsVactisNestPasQualifieEtGardeSonAppui() {
        Medecin medecin = new Medecin();
        medecin.setId(5L);
        when(medecinRepository.findById(5L)).thenReturn(Optional.of(medecin));
        when(retourTerrainRepository.save(any(RetourTerrain.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SaisieVisiteLibreRequest request = new SaisieVisiteLibreRequest();
        request.setMedecinId(5L);
        request.setDateVisite(LocalDate.now());
        request.setTypePassage("service");
        request.setQualification("FAVORABLE");
        request.setCommercialAppui("  Commercial 2 ");

        RetourTerrain retour = actionService.creerVisiteLibre(request, "Commercial 1");

        assertEquals(TypeVisite.SERVICE, retour.getTypeVisite());
        assertEquals(QualificationVisite.NON_RENSEIGNE, retour.getQualification());
        assertEquals("Commercial 2", retour.getCommercialAppui());
    }

    @Test
    void visiteLibreDefavorableAvecObstacleEstEnregistree() {
        Medecin medecin = new Medecin();
        medecin.setId(5L);
        when(medecinRepository.findById(5L)).thenReturn(Optional.of(medecin));
        when(retourTerrainRepository.save(any(RetourTerrain.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SaisieVisiteLibreRequest request = new SaisieVisiteLibreRequest();
        request.setMedecinId(5L);
        request.setDateVisite(LocalDate.now());
        request.setQualification("DEFAVORABLE");
        request.setCommentaire("Trouve nos tarifs trop élevés");
        request.setObstaclePrincipal("prix_tarif");

        RetourTerrain retour = actionService.creerVisiteLibre(request, "Commercial 1");

        assertEquals(QualificationVisite.DEFAVORABLE, retour.getQualification());
        assertEquals(ObstaclePrincipal.PRIX_TARIF, retour.getObstaclePrincipal());
    }

    @Test
    void visiteLibreDefavorableSansObstacleEstRefusee() {
        SaisieVisiteLibreRequest request = new SaisieVisiteLibreRequest();
        request.setMedecinId(5L);
        request.setDateVisite(LocalDate.now());
        request.setQualification("DEFAVORABLE");
        request.setCommentaire("Pas intéressé");

        assertThrows(IllegalArgumentException.class, () -> actionService.creerVisiteLibre(request, "Commercial 1"));
    }

    @Test
    void typeDePassageInconnuEstRefuseEtAppuiIdentiqueAuVisiteurEstIgnore() {
        assertThrows(IllegalArgumentException.class, () -> ActionService.estPassageService("LIVRAISON"));
        assertEquals(false, ActionService.estPassageService(null));
        assertNull(ActionService.commercialAppui("commercial 1", "Commercial 1"));
        assertNull(ActionService.commercialAppui("  ", "Commercial 1"));
    }
}
