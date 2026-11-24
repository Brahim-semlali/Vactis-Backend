package com.vactis.controller;

import com.vactis.dto.bridgetogoal.BridgeGoalTargetRequest;
import com.vactis.dto.medecin.MedecinLocalisationRequest;
import com.vactis.service.ActionService;
import com.vactis.service.AgenceConcurrenteService;
import com.vactis.service.AlerteFinCycleService;
import com.vactis.service.AlerteHebdomadaireService;
import com.vactis.service.BridgeGoalTargetService;
import com.vactis.service.BridgeToGoalService;
import com.vactis.service.ExcelImportExportService;
import com.vactis.service.Fiche360PdfService;
import com.vactis.service.Fiche360Service;
import com.vactis.service.MedecinService;
import com.vactis.service.RetourTerrainService;
import com.vactis.service.system.SystemSettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Deuxième couche de contrôle d'accès : les {@code @PreAuthorize} des contrôleurs (actions réservées à l'administrateur)
 * et les règles « vue direction » codées dans les contrôleurs (alertes de fin de cycle, fiche contextuelle).
 * La première couche (règles d'URL) est couverte par {@code SecurityRulesTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RolesEtDroitsMetierTest.Contexte.class)
class RolesEtDroitsMetierTest {

    @Autowired private AgenceConcurrenteController agences;
    @Autowired private BridgeToGoalController bridgeToGoal;
    @Autowired private SystemSettingsController parametres;
    @Autowired private MedecinController medecins;
    @Autowired private ExcelImportExportController excel;
    @Autowired private AlerteHebdomadaireController alertes;
    @Autowired private ActionController actions;

    @MockitoBean private AgenceConcurrenteService agenceService;
    @MockitoBean private BridgeToGoalService bridgeToGoalService;
    @MockitoBean private BridgeGoalTargetService bridgeGoalTargetService;
    @MockitoBean private SystemSettingsService systemSettingsService;
    @MockitoBean private MedecinService medecinService;
    @MockitoBean private RetourTerrainService retourTerrainService;
    @MockitoBean private Fiche360Service fiche360Service;
    @MockitoBean private Fiche360PdfService fiche360PdfService;
    @MockitoBean private ExcelImportExportService excelService;
    @MockitoBean private AlerteHebdomadaireService alerteHebdomadaireService;
    @MockitoBean private AlerteFinCycleService alerteFinCycleService;
    @MockitoBean private ActionService actionService;

    private static Authentication courant() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Nested
    @DisplayName("Actions réservées à l'administrateur (@PreAuthorize)")
    class ReserveesAdmin {

        @Test
        @WithMockUser(roles = "ADMIN")
        void lAdministrateurGereLesAgencesConcurrentes() {
            agences.deleteAgence(3L);
            verify(agenceService).deleteAgence(3L);
        }

        @Test
        @WithMockUser(authorities = {"ROLE_DIRECTION", "MENU:/zone-intelligence", "MENU:/medecins"})
        void personneDAutreNeModifieLesAgences() {
            assertThrows(AccessDeniedException.class, () -> agences.createAgence(null));
            assertThrows(AccessDeniedException.class, () -> agences.updateAgence(3L, null));
            assertThrows(AccessDeniedException.class, () -> agences.deleteAgence(3L));
            verify(agenceService, never()).deleteAgence(anyLong());
        }

        @Test
        @WithMockUser(authorities = {"ROLE_DIRECTION", "MENU:/bridge-to-goal"})
        void laCibleMensuelleEstReserveeALAdministrateur() {
            assertThrows(AccessDeniedException.class, () -> bridgeToGoal.getMonthlyTarget("2026-11"));
            assertThrows(AccessDeniedException.class,
                    () -> bridgeToGoal.saveMonthlyTarget(new BridgeGoalTargetRequest("2026-11", "DIRECT", BigDecimal.TEN)));
            assertThrows(AccessDeniedException.class, () -> parametres.updateBridgeGoalTarget(BigDecimal.TEN, "DIRECT"));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void lAdministrateurFixeLaCibleMensuelle() {
            bridgeToGoal.saveMonthlyTarget(new BridgeGoalTargetRequest("2026-11", "DIRECT", BigDecimal.TEN));
            parametres.updateBridgeGoalTarget(BigDecimal.TEN, "DIRECT");
            verify(systemSettingsService).updateBridgeGoalTarget(BigDecimal.TEN, "DIRECT");
        }

        @Test
        @WithMockUser(authorities = {"ROLE_COMMERCIALE", "MENU:/medecins"})
        void seulLAdministrateurDeplaceUnMedecinSurLaCarte() {
            assertThrows(AccessDeniedException.class,
                    () -> medecins.updateLocalisation(7L, new MedecinLocalisationRequest(31.6, -8.0)));
            verify(medecinService, never()).updateLocalisation(anyLong(), any(), any());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void lAdministrateurDeplaceUnMedecinSurLaCarte() {
            medecins.updateLocalisation(7L, new MedecinLocalisationRequest(31.6, -8.0));
            verify(medecinService).updateLocalisation(7L, 31.6, -8.0);
        }

        @Test
        @WithMockUser(authorities = {"ROLE_DIRECTION", "MENU:/dashboard-direction"})
        void lExportExcelExigeLeDroitImportExport() {
            assertThrows(AccessDeniedException.class, () -> excel.export(null, null, null));
            assertThrows(AccessDeniedException.class, () -> excel.getFilters());
        }

        @Test
        @WithMockUser(authorities = "MENU:/import-export-excel")
        void leDroitImportExportSuffitPourExporter() throws Exception {
            excel.getFilters();
            verify(excelService).getFilterMetadata();
        }
    }

    @Nested
    @DisplayName("Vue direction calculée dans les contrôleurs")
    class VueDirection {

        @Test
        @WithMockUser(username = "Commercial 1", authorities = {"ROLE_COMMERCIALE", "MENU:/actions", "MENU:/alertes-hebdo"})
        void unCommercialNeRecoitQueSesAlertesDeFinDeCycle() {
            alertes.getAlertesFinCycle(courant());
            verify(alerteFinCycleService).getAlertes(eq("Commercial 1"), eq(false), any());
        }

        @Test
        @WithMockUser(username = "dir", roles = "DIRECTION")
        void laDirectionRecoitLesAlertesDeLEquipe() {
            alertes.getAlertesFinCycle(courant());
            verify(alerteFinCycleService).getAlertes(eq("dir"), eq(true), any());
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void lAdministrateurRecoitLesAlertesDeLEquipe() {
            alertes.getAlertesFinCycle(courant());
            verify(alerteFinCycleService).getAlertes(eq("admin"), eq(true), any());
        }

        @Test
        @WithMockUser(username = "resp", authorities = {"ROLE_RESPONSABLE", "MENU:/dashboard-direction"})
        @DisplayName("Constat d'audit : le droit de menu /dashboard-direction suffit à obtenir la vue direction")
        void leMenuDashboardDirectionDonneLaVueDirection() {
            alertes.getAlertesFinCycle(courant());
            verify(alerteFinCycleService).getAlertes(eq("resp"), eq(true), any());
        }

        @Test
        void sansAuthentificationPasDeVueEquipe() {
            alertes.getAlertesFinCycle(null);
            verify(alerteFinCycleService).getAlertes(eq(null), eq(false), any());
        }

        @Test
        @WithMockUser(authorities = {"ROLE_COMMERCIALE", "MENU:/actions"})
        void laFicheContextuelleMasqueLeMessageDirectionAuCommercial() {
            actions.getFicheContextuelle(7L, courant());
            verify(actionService).getFicheContextuelle(7L, false);
        }

        @Test
        @WithMockUser(roles = "PILOTAGE")
        void laFicheContextuelleMontreLeMessageDirectionAuPilotage() {
            when(actionService.getFicheContextuelle(anyLong(), anyBoolean())).thenReturn(null);
            actions.getFicheContextuelle(7L, courant());
            verify(actionService).getFicheContextuelle(7L, true);
            clearInvocations(actionService);
        }
    }

    @Configuration
    @EnableMethodSecurity
    @Import({AgenceConcurrenteController.class, BridgeToGoalController.class, SystemSettingsController.class,
            MedecinController.class, ExcelImportExportController.class, AlerteHebdomadaireController.class,
            ActionController.class})
    static class Contexte {
    }
}
