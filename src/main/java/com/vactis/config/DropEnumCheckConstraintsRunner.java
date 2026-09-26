package com.vactis.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Supprime les contraintes CHECK générées automatiquement par Hibernate 6
 * sur les colonnes @Enumerated(STRING).
 *
 * Hibernate 6+ avec ddl-auto=update génère des contraintes CHECK DDL pour
 * chaque colonne enum. Ces contraintes posent problème quand on ajoute
 * de nouveaux enums ou qu'on utilise @Deprecated pour la compatibilité.
 *
 * Ce composant s'exécute après le démarrage de l'application et supprime
 * ces contraintes. Couplé au NoCheckConstraintPostgresDialect, cela garantit
 * qu'aucune contrainte enum ne perturbe les mises à jour.
 */
@Component
public class DropEnumCheckConstraintsRunner {

    private static final Logger log = LoggerFactory.getLogger(DropEnumCheckConstraintsRunner.class);

    @Autowired
    private DataSource dataSource;

    private static final String[] DROP_STATEMENTS = {
        // Table medecins
        "ALTER TABLE medecins DROP CONSTRAINT IF EXISTS medecins_statut_pilotage_check",
        "ALTER TABLE medecins DROP CONSTRAINT IF EXISTS medecins_statut_check",
        "ALTER TABLE medecins DROP CONSTRAINT IF EXISTS medecins_segment_check",
        "ALTER TABLE medecins DROP CONSTRAINT IF EXISTS medecins_risque_urgence_check",
        "ALTER TABLE medecins DROP CONSTRAINT IF EXISTS medecins_fiabilite_check",
        "ALTER TABLE medecins DROP CONSTRAINT IF EXISTS medecins_intensite_risque_check",
        // Table actions
        "ALTER TABLE actions DROP CONSTRAINT IF EXISTS actions_statut_check",
        "ALTER TABLE actions DROP CONSTRAINT IF EXISTS actions_etat_action_check",
        "ALTER TABLE actions DROP CONSTRAINT IF EXISTS actions_segment_check",
        "ALTER TABLE actions DROP CONSTRAINT IF EXISTS actions_urgence_check",
        "ALTER TABLE actions DROP CONSTRAINT IF EXISTS actions_priorite_check",
        // Table alertes_hebdomadaires
        "ALTER TABLE alertes_hebdomadaires DROP CONSTRAINT IF EXISTS alertes_hebdomadaires_statut_check",
        "ALTER TABLE alertes_hebdomadaires DROP CONSTRAINT IF EXISTS alertes_hebdomadaires_segment_check",
        // Table retours_terrain
        "ALTER TABLE retours_terrain DROP CONSTRAINT IF EXISTS retours_terrain_statut_check",
        "ALTER TABLE retours_terrain DROP CONSTRAINT IF EXISTS retours_terrain_type_retour_check",
        // Table reclamations
        "ALTER TABLE reclamations DROP CONSTRAINT IF EXISTS reclamations_statut_check",
        "ALTER TABLE reclamations DROP CONSTRAINT IF EXISTS reclamations_type_check",
    };

    @PostConstruct
    public void dropEnumCheckConstraints() {
        log.info("[VACTIS] Suppression des contraintes CHECK d'enum générées par Hibernate...");
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            int droppedCount = 0;
            for (String sql : DROP_STATEMENTS) {
                try {
                    stmt.execute(sql);
                    droppedCount++;
                } catch (SQLException e) {
                    // La contrainte n'existe pas - c'est normal, on continue
                    log.debug("[VACTIS] DROP ignoré (n'existe pas): {} -> {}", sql, e.getMessage());
                }
            }
            log.info("[VACTIS] Nettoyage des contraintes CHECK terminé ({} instructions exécutées)", droppedCount);
        } catch (SQLException e) {
            log.error("[VACTIS] Erreur lors de la suppression des contraintes CHECK: {}", e.getMessage());
            // Ne pas faire échouer le démarrage pour cette raison
        }
    }
}
