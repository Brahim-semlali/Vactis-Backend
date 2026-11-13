package com.vactis.dto.notification;

import java.time.LocalDateTime;

/**
 * DTO exposé par l'API /api/notifications.
 * Ne contient aucune donnée de l'utilisateur (pas de mot de passe, hash ni rôles).
 * Section 5 et 7 : notification à la direction/admin sur création de réclamation.
 */
public record NotificationResponse(
        Long id,
        String titre,
        String message,
        String type,
        Long referenceId,
        Boolean lu,
        LocalDateTime createdAt
) {
    /** Construit un DTO à partir d'une entité Notification. */
    public static NotificationResponse from(com.vactis.model.notification.Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getTitre(),
                n.getMessage(),
                n.getType(),
                n.getReferenceId(),
                n.getLu(),
                n.getCreatedAt()
        );
    }
}
