package com.vactis.config;

import org.hibernate.dialect.PostgreSQLDialect;

/**
 * Custom PostgreSQL dialect qui désactive la génération automatique
 * de CHECK constraints pour les colonnes enum (@Enumerated(STRING)).
 *
 * Dans Hibernate 6+, les colonnes @Enumerated(EnumType.STRING) génèrent
 * automatiquement des contraintes CHECK DDL. Cela pose problème quand :
 * - On ajoute de nouveaux enums (données existantes invalides)
 * - On utilise des enums @Deprecated pour la compatibilité ascendante
 * - Des contraintes "stale" empêchent les mises à jour valides
 *
 * Solution: surcharger getCheckCondition() pour retourner null,
 * supprimant ainsi la génération de la contrainte CHECK.
 * La validation reste gérée par la couche applicative Java.
 */
public class NoCheckConstraintPostgresDialect extends PostgreSQLDialect {

    /**
     * Désactive les check constraints pour les enums définis par leur classe.
     * Signature Hibernate 6.x: getCheckCondition(String, Class<? extends Enum<?>>)
     */
    @Override
    public String getCheckCondition(String columnName, Class<? extends Enum<?>> enumType) {
        return null;
    }

    /**
     * Désactive les check constraints pour les enums définis par leurs valeurs.
     * Signature alternative: getCheckCondition(String, String[])
     */
    @Override
    public String getCheckCondition(String columnName, String[] values) {
        return null;
    }
}
