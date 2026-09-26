package com.vactis.service;

import com.vactis.model.medecin.Medecin;
import org.springframework.stereotype.Service;

/**
 * Moteur narratif contextuel basé sur les 7 matrices de décision VACTIS (Section 8).
 * Génère automatiquement des synthèses narratives précises et personnalisées pour chaque prescripteur.
 */
@Service
public class MoteurCommentaireService {

    public record CommentaireMoteurDto(
            String codeScenario,
            String titreSynthese,
            String explicationDetaillee,
            String actionConseillee
    ) {}

    /**
     * Génère la synthèse narrative contextuelle pour un médecin donné.
     */
    public CommentaireMoteurDto genererCommentaire(Medecin m) {
        if (m == null) {
            return new CommentaireMoteurDto(
                    "INCONNU",
                    "Médecin non identifié",
                    "Données insuffisantes pour générer une synthèse narrative.",
                    "Vérifier le profil et l'historique du prescripteur."
            );
        }

        String nomComplet = ((m.getNom() != null ? m.getNom() : "") + " "
                + (m.getPrenom() != null ? m.getPrenom() : "")).trim().toUpperCase();
        String specialite = m.getSpecialite() != null ? m.getSpecialite() : "Spécialité générale";
        String statut = m.getStatut() != null ? m.getStatut().toUpperCase() : "ACTIF_STABLE";
        int caMois = m.getCaMois() != null ? m.getCaMois() : 0;
        int totalCas = m.getTotalCas() != null ? m.getTotalCas() : 0;
        double variationMixte = m.getVariationMixteSur100() != null ? m.getVariationMixteSur100() : 0.0;
        int joursSansActivite = m.getJoursSansActivite() != null ? m.getJoursSansActivite() : 0;

        // Scénario A : Profil d'activité irrégulière validé (Section 4)
        if (Boolean.TRUE.equals(m.getIsProfilIrregulier())) {
            return new CommentaireMoteurDto(
                    "IRREGULIER_SECURISE",
                    "Activité irrégulière validée (Profil préservé)",
                    String.format(
                            "Le Dr %s (%s) présente une variabilité d'envoi classée comme activité irrégulière selon les critères disponibles. Les creux observés doivent être qualifiés avec le commercial avant toute action renforcée.",
                            nomComplet, specialite
                    ),
                    "Maintenir une veille standard sans visite de crise non sollicitée."
            );
        }

        // Scénario B : Statut manuel "À réactiver" (Section 1)
        if ("A_REACTIVER".equals(statut) || Boolean.TRUE.equals(m.getIsAReactiverManuel())) {
            return new CommentaireMoteurDto(
                    "A_REACTIVER_MANUEL",
                    "Prescripteur prioritaire à réactiver",
                    String.format(
                            "Le Dr %s (%s) a été marqué manuellement pour réactivation. Aucun envoi enregistré depuis %d jours. Historique de collaboration significatif.",
                            nomComplet, specialite, joursSansActivite
                    ),
                    "Planifier une visite de reprise de contact et vérifier les besoins actuels du cabinet."
            );
        }

        // Scénario C : Onboarding
        if ("ONBOARDING".equals(statut)) {
            return new CommentaireMoteurDto(
                    "ONBOARDING_ACTIF",
                    "Nouveau prescripteur en phase d'intégration",
                    String.format(
                            "Intégration récente réussie pour le Dr %s (%s). Premier palier d'activité franchi avec %d cas reçus ce mois (%d MAD).",
                            nomComplet, specialite, totalCas, caMois
                    ),
                    "Effectuer une visite d'accompagnement pour consolider les habitudes d'envoi et présenter l'équipe d'anapath."
            );
        }

        // Scénario D : Inactif (> 60 jours sans aucun dossier)
        if ("INACTIF".equals(statut) || "EXCLU".equals(statut)) {
            return new CommentaireMoteurDto(
                    "INACTIF_60_JOURS",
                    "Prescripteur hors cycle actif (> 60 jours)",
                    String.format(
                            "Rupture de collaboration constatée : le Dr %s (%s) n'a transmis aucun dossier médical depuis %d jours consécutifs. Le seuil des 60 jours est dépassé.",
                            nomComplet, specialite, joursSansActivite
                    ),
                    "Envisager une démarche de qualification commerciale ou archiver si cessation d'activité."
            );
        }

        // Scénario E : Progression forte (> +20%)
        if ("PROGRESSION".equals(statut) || variationMixte > 20.0) {
            return new CommentaireMoteurDto(
                    "PROGRESSION_FORTE",
                    "Trajectoire de croissance confirmée",
                    String.format(
                            "Dynamique très favorable pour le Dr %s (%s) avec une hausse d'activité de +%.1f%% (mixte CA + volume). CA courant : %d MAD sur %d dossiers.",
                            nomComplet, specialite, variationMixte, caMois, totalCas
                    ),
                    "Visite de fidélisation et de remerciement ; opportunité de proposer des examens complémentaires."
            );
        }

        // Scénario F : Surveillance (variation [-40%, -10%])
        if ("SURVEILLANCE".equals(statut)) {
            return new CommentaireMoteurDto(
                    "SURVEILLANCE_FLECHISSEMENT",
                    "Signal d'alerte précoce à surveiller",
                    String.format(
                            "L'activité du Dr %s (%s) fléchit avec une variation mixte de %.1f%% par rapport à sa moyenne récente. %d jours se sont écoulés depuis le dernier envoi.",
                            nomComplet, specialite, variationMixte, joursSansActivite
                    ),
                    "Visite de diagnostic recommandée pour détecter une éventuelle insatisfaction avant décrochage."
            );
        }

        // Scénario G : Rétention / Chute critique (< -40%)
        if ("RETENTION".equals(statut) || "SILENCE_CRITIQUE".equals(statut)) {
            return new CommentaireMoteurDto(
                    "RETENTION_CRITIQUE",
                    "Risque commercial majeur — Protocole Rétention",
                    String.format(
                            "Le Dr %s (%s) présente une baisse d'activité de %.1f%% et %d jours sans dépôt. La cause de cette évolution reste à diagnostiquer sur le terrain.",
                            nomComplet, specialite, variationMixte, joursSansActivite
                    ),
                    "Réaliser l'Action 1 de diagnostic selon l'horizon recommandé et identifier la cause de la baisse."
            );
        }

        // Par défaut : Activité stable
        return new CommentaireMoteurDto(
                "ACTIF_STABLE_REGULIER",
                "Activité régulière et pérenne",
                String.format(
                        "Activité régulière pour le Dr %s (%s). Variation stable de %.1f%%, avec %d dossiers traités ce mois.",
                        nomComplet, specialite, variationMixte, totalCas
                ),
                "Poursuivre le rythme de suivi de routine et maintenir la satisfaction relationnelle."
        );
    }
}
