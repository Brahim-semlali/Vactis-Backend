package com.vactis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vactis.dto.notification.NotificationResponse;
import com.vactis.model.Roles.Roles;
import com.vactis.model.auth.Users;
import com.vactis.model.notification.Notification;
import com.vactis.model.menu.MenuItem;
import com.vactis.model.reclamation.CategorieReclamation;
import com.vactis.model.reclamation.PrioriteReclamation;
import com.vactis.model.reclamation.Reclamation;
import com.vactis.model.reclamation.StatutReclamation;
import com.vactis.repository.auth.UserRepository;
import com.vactis.repository.notification.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires du service de notifications in-app (Sections 5 et 7).
 * Vérifie :
 *  - Les destinataires ont accès au menu des réclamations
 *  - La sérialisation JSON sans données utilisateur sensibles
 *  - Le rejet de marquer-lue pour un autre utilisateur
 *  - Que l'API ne renvoie que les notifications de l'utilisateur connecté
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationService notificationService;

    private Users adminUser;
    private Users commercialUser;
    private Reclamation reclamation;

    @BeforeEach
    void setUp() {
        Roles adminRole = new Roles();
        adminRole.setIdRole(1L);
        adminRole.setNameRole("ADMIN");

        adminUser = new Users();
        adminUser.setId(1L);
        adminUser.setUsername("admin_test");
        MenuItem reclamationsMenu = new MenuItem();
        reclamationsMenu.setRoute(NotificationService.ROUTE_RECLAMATIONS);
        adminRole.setMenuItems(List.of(reclamationsMenu));
        adminUser.setRoles(adminRole);

        Roles commercialRole = new Roles();
        commercialRole.setIdRole(2L);
        commercialRole.setNameRole("COMMERCIALE");

        commercialUser = new Users();
        commercialUser.setId(2L);
        commercialUser.setUsername("commercial_test");
        commercialUser.setRoles(commercialRole);

        reclamation = new Reclamation();
        reclamation.setId(42L);
        reclamation.setCommercialDeclarant("commercial_test");
        reclamation.setCategorie(CategorieReclamation.DELAIS_RESULTATS);
        reclamation.setPriorite(PrioriteReclamation.HAUTE);
        reclamation.setStatut(StatutReclamation.A_TRAITER);
    }

    @Test
    @DisplayName("notifierDirectionPourReclamation — crée une notification pour les utilisateurs ayant accès")
    void notifie_utilisateurs_avec_acces_lors_creation_reclamation() {
        when(userRepository.findActiveWithMenuRoute(NotificationService.ROUTE_RECLAMATIONS))
                .thenReturn(List.of(adminUser));
        when(notificationRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Notification> notifications = notificationService.notifierDirectionPourReclamation(reclamation);

        assertEquals(1, notifications.size());
        assertEquals("RECLAMATION", notifications.get(0).getType());
        assertEquals(42L, notifications.get(0).getReferenceId());
        assertEquals(adminUser, notifications.get(0).getUser());
        verify(notificationRepository).saveAll(argThat(list ->
                ((List<?>) list).size() == 1
        ));
    }

    @Test
    @DisplayName("notifierDirectionPourReclamation — ne notifie pas un utilisateur sans accès")
    void ne_notifie_pas_utilisateur_sans_acces() {
        when(userRepository.findActiveWithMenuRoute(NotificationService.ROUTE_RECLAMATIONS))
                .thenReturn(List.of());

        List<Notification> notifications = notificationService.notifierDirectionPourReclamation(reclamation);

        assertEquals(0, notifications.size());
        verify(notificationRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("notifierDirectionPourReclamation — tous les utilisateurs ayant accès sont notifiés")
    void notifie_tous_les_utilisateurs_avec_acces() {
        Users commercialAutorise = new Users();
        commercialAutorise.setId(3L);
        commercialAutorise.setUsername("commercial_autorise");
        Roles commercialRole = new Roles();
        commercialRole.setNameRole("COMMERCIALE");
        MenuItem reclamationsMenu = new MenuItem();
        reclamationsMenu.setRoute(NotificationService.ROUTE_RECLAMATIONS);
        commercialRole.setMenuItems(List.of(reclamationsMenu));
        commercialAutorise.setRoles(commercialRole);

        when(userRepository.findActiveWithMenuRoute(NotificationService.ROUTE_RECLAMATIONS))
                .thenReturn(List.of(adminUser, commercialAutorise));
        when(notificationRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Notification> notifications = notificationService.notifierDirectionPourReclamation(reclamation);

        assertEquals(2, notifications.size());
    }

    @Test
    @DisplayName("getNotifications — retourne uniquement les notifications de l'utilisateur connecté")
    void getNotifications_retourne_seulement_les_siennes() {
        Notification notif1 = Notification.builder()
                .id(1L)
                .user(adminUser)
                .titre("Test")
                .message("Message")
                .type("RECLAMATION")
                .referenceId(42L)
                .lu(false)
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.findByUsername("admin_test")).thenReturn(Optional.of(adminUser));
        when(notificationRepository.findByUserOrderByCreatedAtDesc(adminUser)).thenReturn(List.of(notif1));

        List<NotificationResponse> result = notificationService.getNotifications("admin_test");

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).id());
        assertEquals("RECLAMATION", result.get(0).type());
    }

    @Test
    @DisplayName("getNotifications — l'utilisateur inconnu reçoit une liste vide")
    void getNotifications_utilisateur_inconnu_renvoie_vide() {
        when(userRepository.findByUsername("inconnu")).thenReturn(Optional.empty());

        List<NotificationResponse> result = notificationService.getNotifications("inconnu");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("marquerLue — 403 si la notification appartient à un autre utilisateur")
    void marquerLue_interdit_pour_autre_utilisateur() {
        Notification notif = Notification.builder()
                .id(10L)
                .user(adminUser)  // appartient à admin_test
                .titre("Test")
                .message("Msg")
                .type("RECLAMATION")
                .referenceId(1L)
                .lu(false)
                .createdAt(LocalDateTime.now())
                .build();

        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notif));

        // commercial_test essaie de marquer la notification de admin_test
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> notificationService.marquerLue(10L, "commercial_test"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("marquerLue — OK si la notification appartient bien à l'utilisateur connecté")
    void marquerLue_ok_pour_proprio() {
        Notification notif = Notification.builder()
                .id(10L)
                .user(adminUser)
                .titre("Test")
                .message("Msg")
                .type("RECLAMATION")
                .referenceId(1L)
                .lu(false)
                .createdAt(LocalDateTime.now())
                .build();

        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notif));
        when(notificationRepository.save(any(Notification.class))).thenReturn(notif);

        notificationService.marquerLue(10L, "admin_test");

        verify(notificationRepository).save(argThat(n -> Boolean.TRUE.equals(n.getLu())));
    }

    @Test
    @DisplayName("NotificationResponse — la sérialisation JSON ne contient pas password, hash ni rôles")
    void notificationResponse_json_sans_donnees_sensibles() throws Exception {
        NotificationResponse dto = new NotificationResponse(
                1L, "Titre", "Message", "RECLAMATION", 42L, false, LocalDateTime.now()
        );

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String json = mapper.writeValueAsString(dto);

        assertFalse(json.contains("password"), "Le JSON ne doit pas contenir 'password'");
        assertFalse(json.contains("hash"), "Le JSON ne doit pas contenir 'hash'");
        assertFalse(json.contains("roles"), "Le JSON ne doit pas contenir 'roles'");
        assertFalse(json.contains("username"), "Le JSON ne doit pas contenir 'username'");
        assertTrue(json.contains("\"titre\""), "Le JSON doit contenir le titre");
        assertTrue(json.contains("RECLAMATION"), "Le JSON doit contenir le type");
    }

    @Test
    @DisplayName("getNonLuesCount — compte les notifications non lues de l'utilisateur")
    void getNonLuesCount_renvoie_bon_compte() {
        when(userRepository.findByUsername("admin_test")).thenReturn(Optional.of(adminUser));
        when(notificationRepository.countByUserAndLuFalse(adminUser)).thenReturn(3L);

        long count = notificationService.getNonLuesCount("admin_test");

        assertEquals(3L, count);
    }
}
