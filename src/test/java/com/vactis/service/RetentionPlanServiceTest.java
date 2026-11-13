package com.vactis.service;

import com.vactis.model.action.Action;
import com.vactis.model.action.EtatAction;
import com.vactis.model.action.StatutPlanRetention;
import com.vactis.model.action.TypeEtapeRetention;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.reclamation.Reclamation;
import com.vactis.model.reclamation.StatutReclamation;
import com.vactis.repository.ActionRepository;
import com.vactis.repository.ReclamationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;

@ExtendWith(MockitoExtension.class)
class RetentionPlanServiceTest {

    @Mock private ActionRepository actionRepository;
    @Mock private ReclamationRepository reclamationRepository;
    @InjectMocks private RetentionPlanService service;

    @Test
    void doesNotCreateActionTwoWhenActionOneWasNotPerformed() {
        Action actionOne = retentionAction(TypeEtapeRetention.ACTION_1, EtatAction.PLANIFIEE, "2026-08");
        when(actionRepository.findAll()).thenReturn(List.of(actionOne));

        List<Action> created = service.evaluerPlansRetention("2026-09");

        assertEquals(0, created.size());
        assertEquals(StatutPlanRetention.EN_COURS, actionOne.getStatutPlanRetention());
        verify(actionRepository).saveAll(List.of(actionOne));
    }

    @Test
    void closesPlanWhenNextCycleStatusImproves() {
        Action actionOne = retentionAction(TypeEtapeRetention.ACTION_1, EtatAction.REALISEE, "2026-08");
        actionOne.getMedecin().setStatut("PROGRESSION");
        when(actionRepository.findAll()).thenReturn(List.of(actionOne));

        List<Action> created = service.evaluerPlansRetention("2026-09");

        assertEquals(0, created.size());
        assertEquals(StatutPlanRetention.CLOTURE, actionOne.getStatutPlanRetention());
    }

    @Test
    void complaintOnActionOneSuspendsPlanWithoutCreatingActionTwo() {
        Action actionOne = retentionAction(TypeEtapeRetention.ACTION_1, EtatAction.REALISEE, "2026-08");
        actionOne.setQualification("RECLAMATION");
        actionOne.setReclamationId(41L);
        when(actionRepository.findAll()).thenReturn(List.of(actionOne));

        List<Action> created = service.evaluerPlansRetention("2026-08");

        assertEquals(0, created.size());
        assertEquals(StatutPlanRetention.EN_OBSERVATION, actionOne.getStatutPlanRetention());
    }

    @Test
    void waitsUntilTheFirstCompleteCycleAfterActionOneBeforeCreatingActionTwo() {
        Action actionOne = retentionAction(TypeEtapeRetention.ACTION_1, EtatAction.REALISEE, "2026-08");
        when(actionRepository.findAll()).thenReturn(List.of(actionOne));
        when(actionRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(0, service.evaluerPlansRetention("2026-08").size());
        assertEquals(1, service.evaluerPlansRetention("2026-09").size());
    }

    @Test
    void createsOnlyOneCorrectiveActionTwoForPersistentRetention() {
        Action actionOne = retentionAction(TypeEtapeRetention.ACTION_1, EtatAction.REALISEE, "2026-08");
        when(actionRepository.findAll()).thenReturn(List.of(actionOne));
        when(actionRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Action> created = service.evaluerPlansRetention("2026-09");

        assertEquals(1, created.size());
        assertEquals(TypeEtapeRetention.ACTION_2, created.get(0).getTypeEtapeRetention());
        assertEquals(actionOne.getId(), created.get(0).getActionParenteId());
        assertEquals("RETENTION", created.get(0).getStatut());
    }

    @Test
    void doesNotMarkActionTwoFailedUntilItsFollowingCompleteCycle() {
        Action actionTwo = retentionAction(TypeEtapeRetention.ACTION_2, EtatAction.REALISEE, "2026-08");
        when(actionRepository.findAll()).thenReturn(List.of(actionTwo));

        service.evaluerPlansRetention("2026-08");
        assertEquals(StatutPlanRetention.EN_COURS, actionTwo.getStatutPlanRetention());

        service.evaluerPlansRetention("2026-09");
        assertEquals(StatutPlanRetention.ECHEC, actionTwo.getStatutPlanRetention());
        assertEquals("Arbitrage de la direction requis", actionTwo.getProchaineAction());
        assertTrue(actionTwo.getCommentaire().contains("Arbitrage de la direction"));
    }

    @Test
    void doesNotFailAnOverdueActionBeforeItIsPerformedAndEvaluatedNextCycle() {
        Action actionTwo = retentionAction(TypeEtapeRetention.ACTION_2, EtatAction.PLANIFIEE, "2026-08");
        actionTwo.setDateEcheance(LocalDate.of(2026, 8, 1));
        when(actionRepository.findAll()).thenReturn(List.of(actionTwo));

        service.evaluerPlansRetention("2026-10");

        assertEquals(StatutPlanRetention.EN_COURS, actionTwo.getStatutPlanRetention());
    }

    @Test
    void suspendedLeavePlanResumesOnlyAfterReturnOrReviewCycle() {
        Action action = retentionAction(TypeEtapeRetention.ACTION_1, EtatAction.REALISEE, "2026-08");
        action.setStatutPlanRetention(StatutPlanRetention.EN_OBSERVATION);
        action.setQualification("CONGE_ABSENCE");
        action.setDateDepart(LocalDate.of(2026, 8, 5));
        action.setDateRevue(LocalDate.of(2026, 9, 15));
        when(actionRepository.findAll()).thenReturn(List.of(action));

        service.evaluerPlansRetention("2026-09");
        assertEquals(StatutPlanRetention.EN_OBSERVATION, action.getStatutPlanRetention());
        service.evaluerPlansRetention("2026-10");
        assertEquals(StatutPlanRetention.EN_COURS, action.getStatutPlanRetention());
        assertTrue(action.getSuspensionLevee());
        List<Action> action2 = service.evaluerPlansRetention("2026-11");
        assertEquals(1, action2.size());
        assertEquals(TypeEtapeRetention.ACTION_2, action2.get(0).getTypeEtapeRetention());
    }

    @Test
    void suspendsImmediatelyForLeaveWithoutWaitingForCycleClose() {
        Action action = retentionAction(TypeEtapeRetention.ACTION_1, EtatAction.REALISEE, "2026-08");
        action.setQualification("CONGE_ABSENCE");
        action.setDateDepart(LocalDate.of(2026, 8, 10));
        action.setDateRevue(LocalDate.of(2026, 9, 15));
        when(actionRepository.findAll()).thenReturn(List.of(action));

        service.evaluerPlansRetention("2026-08");

        assertEquals(StatutPlanRetention.EN_OBSERVATION, action.getStatutPlanRetention());
    }

    @Test
    void suspendedComplaintPlanResumesAfterDirectionTreatmentAndLaterCycle() {
        Action action = retentionAction(TypeEtapeRetention.ACTION_1, EtatAction.REALISEE, "2026-08");
        action.setStatutPlanRetention(StatutPlanRetention.EN_OBSERVATION);
        action.setQualification("RECLAMATION");
        action.setReclamationId(90L);
        Reclamation reclamation = new Reclamation();
        reclamation.setStatut(StatutReclamation.TRAITEE);
        reclamation.setDateResolution(LocalDate.of(2026, 9, 5));
        when(actionRepository.findAll()).thenReturn(List.of(action));
        when(reclamationRepository.findById(90L)).thenReturn(java.util.Optional.of(reclamation));

        service.evaluerPlansRetention("2026-09");
        assertEquals(StatutPlanRetention.EN_OBSERVATION, action.getStatutPlanRetention());
        service.evaluerPlansRetention("2026-10");
        assertEquals(StatutPlanRetention.EN_COURS, action.getStatutPlanRetention());
        assertTrue(action.getSuspensionLevee());
    }

    private Action retentionAction(TypeEtapeRetention etape, EtatAction etat, String cycle) {
        Medecin medecin = new Medecin();
        medecin.setId(7L);
        medecin.setNom("Test");
        medecin.setStatut("RETENTION");
        Action action = new Action();
        action.setId(21L);
        action.setMedecin(medecin);
        action.setCommercial("commercial");
        action.setLieuOrganisme("cabinet");
        action.setTypeEtapeRetention(etape);
        action.setEtatAction(etat);
        action.setStatutPlanRetention(StatutPlanRetention.EN_COURS);
        action.setCycleMensuel(cycle);
        action.setDateVisite(LocalDate.of(2026, 8, 10));
        return action;
    }
}