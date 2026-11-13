package com.vactis.controller;

import com.vactis.dto.notification.NotificationResponse;
import com.vactis.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST pour les notifications in-app (Sections 5 et 7).
 *
 * Toutes les routes exigent une authentification.
 * Ne renvoie jamais l'entité Notification directement (pas de données utilisateur).
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * GET /api/notifications — retourne les notifications de l'utilisateur connecté uniquement.
     * Retourne un DTO NotificationResponse sans données utilisateur.
     */
    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getNotifications(Authentication authentication) {
        requireAuthentication(authentication);
        return ResponseEntity.ok(notificationService.getNotifications(authentication.getName()));
    }

    /**
     * GET /api/notifications/count — nombre de notifications non lues pour l'utilisateur connecté.
     */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> getCount(Authentication authentication) {
        requireAuthentication(authentication);
        return ResponseEntity.ok(Map.of("count", notificationService.getNonLuesCount(authentication.getName())));
    }

    /**
     * PATCH /api/notifications/{id}/lire — marque une notification comme lue.
     * Retourne 403 si la notification n'appartient pas à l'utilisateur connecté.
     */
    @PatchMapping("/{id}/lire")
    public ResponseEntity<Void> marquerLue(@PathVariable Long id, Authentication authentication) {
        requireAuthentication(authentication);
        notificationService.marquerLue(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    private void requireAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Authentification requise.");
        }
    }
}
