package com.vactis.controller;

import com.vactis.model.reclamation.Reclamation;
import com.vactis.model.reclamation.StatutReclamation;
import com.vactis.service.ReclamationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test de securite (controle d'acces API - 403 Forbidden) pour le module Reclamations.
 * Regle 1.d : controle d'acces cote API (test 403 pour role non direction/admin).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ReclamationSecurityTest.TestConfig.class)
class ReclamationSecurityTest {

    @Autowired
    private ReclamationController controller;

    @Autowired
    private ReclamationService reclamationService;

    @Test
    @WithMockUser(username = "commercial_user", roles = "COMMERCIALE")
    @DisplayName("Un commercial autorisé voit les réclamations créées par tous les utilisateurs")
    void commercialCanReadAllReclamations() {
        Reclamation first = new Reclamation();
        first.setId(10L);
        first.setCommercialDeclarant("admin");
        Reclamation second = new Reclamation();
        second.setId(11L);
        second.setCommercialDeclarant("autre_commercial");
        when(reclamationService.searchReclamations(null, null, null, null)).thenReturn(List.of(first, second));

        var response = controller.searchReclamations(null, null, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(10L, 11L), response.getBody().stream().map(Reclamation::getId).toList());
        verify(reclamationService).searchReclamations(null, null, null, null);
    }

    @Test
    @WithMockUser(username = "commercial_user", roles = "COMMERCIALE")
    @DisplayName("403 Forbidden lorsqu'un commercial tente de modifier le statut d'une reclamation")
    void commercialCannotUpdateStatut_Returns403() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Map<String, String> body = Map.of(
            "statut", "TRAITEE",
            "solution", "Solution commerciale non autorisee"
        );

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> controller.updateStatut(10L, body, auth)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Accès réservé"));
    }

    @Test
    @WithMockUser(username = "directeur_user", roles = "DIRECTION")
    @DisplayName("200 OK lorsqu'un utilisateur Direction modifie le statut avec solution et prochaine instruction")
    void directionCanUpdateStatut_Returns200() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Map<String, String> body = Map.of(
            "statut", "TRAITEE",
            "solution", "Protocole express valide par la direction",
            "responsable", "Directeur Medical",
            "prochaineInstruction", "Informer le praticien sous 24h",
            "dateTraitement", "2026-05-18"
        );

        Reclamation mockRec = new Reclamation();
        mockRec.setId(10L);
        mockRec.setStatut(StatutReclamation.TRAITEE);
        mockRec.setSolutionApportee("Protocole express valide par la direction");
        mockRec.setDateResolution(LocalDate.of(2026, 5, 18));
        when(reclamationService.mettreAJourStatut(eq(10L), eq(StatutReclamation.TRAITEE),
            anyString(), anyString(), anyString(), any(LocalDate.class), anyString()))
            .thenReturn(mockRec);

        var response = controller.updateStatut(10L, body, auth);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(StatutReclamation.TRAITEE, response.getBody().getStatut());
    }

    @Test
    @WithMockUser(username = "admin_user", roles = "ADMIN")
    @DisplayName("200 OK lorsqu'un administrateur modifie le statut")
    void adminCanUpdateStatut_Returns200() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Map<String, String> body = Map.of(
            "statut", "EN_COURS",
            "responsable", "Responsable Admin"
        );

        Reclamation mockRec = new Reclamation();
        mockRec.setId(10L);
        mockRec.setStatut(StatutReclamation.EN_COURS);
        when(reclamationService.mettreAJourStatut(eq(10L), eq(StatutReclamation.EN_COURS),
            isNull(), eq("Responsable Admin"), isNull(), isNull(), anyString()))
            .thenReturn(mockRec);

        var response = controller.updateStatut(10L, body, auth);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Configuration
    @EnableMethodSecurity
    @Import(ReclamationController.class)
    static class TestConfig {
        @Bean
        ReclamationService reclamationService() {
            return mock(ReclamationService.class);
        }
    }
}
