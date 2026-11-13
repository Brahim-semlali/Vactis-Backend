package com.vactis.service;

import com.vactis.dto.notification.NotificationResponse;
import com.vactis.model.auth.Users;
import com.vactis.model.notification.Notification;
import com.vactis.model.reclamation.Reclamation;
import com.vactis.repository.auth.UserRepository;
import com.vactis.repository.notification.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service de notification in-app (Sections 5 et 7 du document VACTIS).
 *
 * Règles d'implémentation :
 * - Les destinataires sont les utilisateurs actifs ayant accès au menu réclamations.
 * - La notification est créée dans la MÊME transaction que la réclamation (propagation MANDATORY).
 * - Le DTO NotificationResponse est utilisé dans le contrôleur : aucune donnée utilisateur exposée.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    static final String ROUTE_RECLAMATIONS = "/reclamations";

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    /**
     * Crée une notification pour chaque utilisateur admin/direction suite à la création d'une réclamation.
     * Exécuté dans la même transaction que la réclamation (Propagation.MANDATORY).
     * Si la sauvegarde échoue, la transaction parente est rollbackée.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Notification> notifierDirectionPourReclamation(Reclamation reclamation) {
        if (reclamation == null) return List.of();

        List<Users> destinataires = userRepository.findActiveWithMenuRoute(ROUTE_RECLAMATIONS);

        if (destinataires.isEmpty()) {
            log.warn("Aucun utilisateur avec accès {} trouvé pour la notification de réclamation #{}",
                    ROUTE_RECLAMATIONS, reclamation.getId());
            return List.of();
        }

        String medecinNom = reclamation.getMedecin() != null
                ? reclamation.getMedecin().getNom() : "Prescripteur";
        String titre = "Nouvelle réclamation à traiter";
        String message = String.format(
                "Réclamation déclarée pour le Dr %s par %s. Catégorie : %s, Priorité : %s.",
                medecinNom, reclamation.getCommercialDeclarant(),
                reclamation.getCategorie(), reclamation.getPriorite());

        List<Notification> notifications = destinataires.stream()
                .map(user -> Notification.builder()
                        .user(user)
                        .titre(titre)
                        .message(message)
                        .type("RECLAMATION")
                        .referenceId(reclamation.getId())
                        .lu(false)
                        .createdAt(LocalDateTime.now())
                        .build())
                .toList();

        List<Notification> saved = notificationRepository.saveAll(notifications);
        log.info("{} notification(s) créées pour les utilisateurs {} suite à la réclamation #{}",
                saved.size(), ROUTE_RECLAMATIONS, reclamation.getId());
        return saved;
    }

    /**
     * Retourne les notifications de l'utilisateur connecté (les siennes uniquement).
     */
    public List<NotificationResponse> getNotifications(String username) {
        try {
            Users user = userRepository.findByUsername(username).orElse(null);
            if (user == null) return List.of();
            return notificationRepository.findByUserOrderByCreatedAtDesc(user)
                    .stream().map(NotificationResponse::from).toList();
        } catch (Exception e) {
            log.warn("Erreur lecture notifications pour {}: {}", username, e.getMessage());
            return List.of();
        }
    }

    /**
     * Retourne le nombre de notifications non lues de l'utilisateur connecté.
     */
    public long getNonLuesCount(String username) {
        try {
            Users user = userRepository.findByUsername(username).orElse(null);
            if (user == null) return 0;
            return notificationRepository.countByUserAndLuFalse(user);
        } catch (Exception e) {
            log.warn("Erreur comptage notifications pour {}: {}", username, e.getMessage());
            return 0;
        }
    }

    /**
     * Marque une notification comme lue. Refusé si la notification n'appartient pas à l'utilisateur.
     *
     * @throws org.springframework.web.server.ResponseStatusException 403 si la notification appartient à un autre utilisateur
     */
    @Transactional
    public void marquerLue(Long notificationId, String username) {
        Notification notif = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Notification introuvable."));
        if (notif.getUser() == null || !username.equals(notif.getUser().getUsername())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Cette notification ne vous appartient pas.");
        }
        notif.setLu(true);
        notificationRepository.save(notif);
    }
}
