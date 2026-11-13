package com.vactis.service;

import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.RisqueUrgence;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

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
        String statut = m.getStatut() != null ? m.getStatut().toUpperCase(Locale.ROOT) : "STATUT_NON_RENSEIGNE";
        Integer caMois = m.getCaMois();
        Integer totalCas = m.getTotalCas();
        Double variationMixte = m.getVariationMixteSur100();
        Integer joursSansActivite = m.getJoursSansActivite();

        String segment = m.getSegment() == null ? null : m.getSegment().toUpperCase(Locale.ROOT);
        Double intervalle = m.getIntervalleEffectif() != null && m.getIntervalleEffectif() > 0
                ? m.getIntervalleEffectif() : null;
        boolean silenceCritique = joursSansActivite != null && intervalle != null
                && joursSansActivite >= (2 * intervalle);
        boolean silenceContradictoire = joursSansActivite != null && intervalle != null
                && joursSansActivite > (intervalle * 1.5);
        boolean scoreNonFiable = "NON_FIABLE".equalsIgnoreCase(m.getFiabilite());
        boolean isStrategique = "A".equals(segment) || "B".equals(segment)
                || (m.getNoteInput() != null && m.getNoteInput() >= 4.0)
                || (m.getPotentielSur100() != null && m.getPotentielSur100() >= 80.0);

        // Scénario 1 : Silence stratégique (Section 8 & Section 9)
        // Rupture critique observée concernant un médecin stratégique (Segment A/B)
        if (isStrategique && (silenceCritique || "SILENCE_CRITIQUE".equals(statut))) {
            return new CommentaireMoteurDto(
                    "SILENCE_STRATEGIQUE",
                    "Silence critique stratégique (Médecin clé)",
                    join("Rupture d'envoi observée pour le Dr " + nomComplet + " (" + specialite + segmentFragment(segment) + ")",
                            daysFragment(joursSansActivite, "sans dossier depuis"),
                            intervalle == null ? null : "intervalle attendu : " + number(intervalle) + " j",
                            "valeur et potentiel prioritaires."),
                    "Comprendre rapidement la rupture et sécuriser la relation."
            );
        }

        // Scénario 2 : Activité irrégulière (Section 4 & Section 8)
        if (Boolean.TRUE.equals(m.getIsProfilIrregulier()) || "ACTIVITE_IRREGULIERE".equals(statut)) {
            return new CommentaireMoteurDto(
                    "IRREGULIER_SECURISE",
                    "Activité irrégulière validée (Intermittence historique)",
                    join("Le Dr " + nomComplet + " (" + specialite + ") présente une intermittence historique confirmée sans défaillance structurelle",
                            daysFragment(joursSansActivite, "jours sans activité"),
                            m.getFiabilite() == null ? null : "fiabilité " + m.getFiabilite() + "."),
                    "Qualifier ou demander un arbitrage avant une action renforcée."
            );
        }

        // Scénario 3 : Statut manuel "À réactiver" (Section 1)
        if ("A_REACTIVER".equals(statut) || Boolean.TRUE.equals(m.getIsAReactiverManuel())) {
            return new CommentaireMoteurDto(
                    "A_REACTIVER_MANUEL",
                    "Prescripteur prioritaire à réactiver",
                    join("Le Dr " + nomComplet + " (" + specialite + ") a été marqué manuellement pour réactivation",
                            daysFragment(joursSansActivite, "sans envoi depuis"),
                            "historique de collaboration significatif."),
                    "Planifier une visite de reprise de contact et vérifier les besoins actuels du cabinet."
            );
        }

        // Scénario 4 : Onboarding ou score non fiable (Section 8)
        if ("ONBOARDING".equals(statut) || scoreNonFiable) {
            return new CommentaireMoteurDto(
                    "ONBOARDING_ACTIF",
                    "Prescripteur en intégration ou historique récent",
                    join("Historique récent ou insuffisant pour le Dr " + nomComplet + " (" + specialite + ")",
                            daysFragment(joursSansActivite, "depuis la dernière activité"),
                            totalCas == null ? null : totalCas + " cas reçus ce mois",
                            amountFragment(caMois, "CA du mois")),
                    "Qualifier le potentiel et le contexte ; ne pas présenter une urgence business comme confirmée."
            );
        }

        // Scénario 5 : Inactif (au moins 60 jours sans activité) (Section 1)
        if ("INACTIF".equals(statut) || "EXCLU".equals(statut)) {
            boolean seuilAtteint = joursSansActivite != null && joursSansActivite >= 60;
            String titreInactif = seuilAtteint
                    ? "Prescripteur hors cycle actif (seuil de 60 jours atteint)"
                    : "Statut inactif à vérifier";
            String detailInactif = joursSansActivite == null
                    ? "Le statut du Dr " + nomComplet + " (" + specialite + ") est " + statut + ", mais la durée sans activité n'est pas disponible; vérifier les données avant de confirmer le seuil de 60 jours."
                    : seuilAtteint
                        ? "Aucun dossier médical n'a été enregistré pour le Dr " + nomComplet + " (" + specialite + ") depuis " + joursSansActivite + " jours consécutifs; le seuil de 60 jours est atteint."
                        : "Le statut du Dr " + nomComplet + " (" + specialite + ") est " + statut + ", mais " + joursSansActivite + " jours mesurés restent sous le seuil de 60 jours; vérifier la cohérence du statut et des données.";
            return new CommentaireMoteurDto(
                    seuilAtteint ? "INACTIF_60_JOURS" : "INACTIF_SEUIL_A_VERIFIER",
                    titreInactif,
                    detailInactif,
                    "Envisager une démarche de qualification commerciale ou archiver si cessation d'activité."
            );
        }

        // Scénario 6 : Rétention / Chute critique (< -40%) (Section 8)
        if ("RETENTION".equals(statut)) {
            return new CommentaireMoteurDto(
                    "RETENTION_CRITIQUE",
                    "Risque commercial majeur — Protocole Rétention",
                    join("Le Dr " + nomComplet + " (" + specialite + ") présente une baisse d'activité",
                            percentFragment(variationMixte, "variation mixte"),
                            amountFragment(caMois, "CA courant"),
                            amountFragment(m.getReferenceCa(), "CA de référence"),
                            daysFragment(joursSansActivite, "sans dépôt")),
                    "Diagnostiquer la cause avant aggravation de la perte."
            );
        }

        // Scénario 7 : Surveillance (variation [-40%, -10%]) (Section 8)
        if ("SURVEILLANCE".equals(statut)) {
            return new CommentaireMoteurDto(
                    "SURVEILLANCE_FLECHISSEMENT",
                    "Signal d'alerte précoce à surveiller",
                    join("Baisse modérée pour le Dr " + nomComplet + " (" + specialite + ")",
                            percentFragment(variationMixte, "variation mixte"),
                            amountFragment(caMois, "CA courant"),
                            amountFragment(m.getReferenceCa(), "CA de référence"),
                            daysFragment(joursSansActivite, "de silence")),
                    "Diagnostiquer avant passage en rétention."
            );
        }

        // Scénario 8 : Progression (> +20%) (Section 8)
                if ("PROGRESSION".equals(statut) || (variationMixte != null && variationMixte > 20.0)) {
                        String objectif = Boolean.TRUE.equals(silenceContradictoire)
                    ? "Reconnaître et fidéliser ; silence contradictoire détecté, contrôler le changement de rythme."
                    : "Reconnaître et fidéliser ; opportunité de proposer des examens complémentaires.";
            return new CommentaireMoteurDto(
                    "PROGRESSION_FORTE",
                    "Trajectoire de croissance confirmée",
                    join("Dynamique de progression pour le Dr " + nomComplet + " (" + specialite + segmentFragment(segment) + ")",
                            percentFragment(variationMixte, "variation mixte"),
                            amountFragment(caMois, "CA du mois"),
                            totalCas == null ? null : totalCas + " dossiers",
                            daysFragment(joursSansActivite, "de silence")),
                    objectif
            );
        }

        // Scénario 9 : Actif / Stabilité (Section 8)
        if (variationMixte == null && !"ACTIF_STABLE".equals(statut) && !"ACTIF".equals(statut)) {
            return new CommentaireMoteurDto(
                    "DONNEES_INSUFFISANTES",
                    "Activité à qualifier",
                    "Les données disponibles ne permettent pas de confirmer une progression, une stabilité ou une baisse pour le Dr "
                            + nomComplet + " (" + specialite + ").",
                    "Compléter les données de CA et de dossiers avant de qualifier la situation."
            );
        }

        String objectifActif = Boolean.TRUE.equals(silenceContradictoire)
                ? "Consolider la relation ; silence contradictoire détecté, effectuer un contrôle préventif."
                : "Consolider la relation et maintenir la satisfaction relationnelle.";
        return new CommentaireMoteurDto(
                "ACTIF_STABLE_REGULIER",
                "Activité régulière et pérenne",
                variationMixte == null
                        ? "Le Dr " + nomComplet + " (" + specialite + ") est enregistré comme " + statut + ", mais aucune variation mesurée n'est disponible; la stabilité n'est pas confirmée."
                        : join("Stabilité observée pour le Dr " + nomComplet + " (" + specialite + segmentFragment(segment) + ")",
                                percentFragment(variationMixte, "variation mixte"),
                                totalCas == null ? null : totalCas + " dossiers traités ce mois",
                                daysFragment(joursSansActivite, "de silence")),
                objectifActif
        );
    }

        public String genererResumeCourt(Medecin medecin, CommentaireMoteurDto commentaire) {
                String statut = medecin.getStatut() == null ? "STATUT NON RENSEIGNÉ" : medecin.getStatut();
                String variation = medecin.getVariationMixteSur100() == null
                                ? "signal d'activité non renseigné"
                                : String.format("variation mixte %.1f%%", medecin.getVariationMixteSur100());
                String niveau = medecin.getRisqueUrgence() == null
                                ? "attention à qualifier"
                                : "attention " + medecin.getRisqueUrgence().name().toLowerCase();
                return String.format("%s — %s — %s", statut, variation, niveau);
        }

        public String genererCommentaireDetaille(CommentaireMoteurDto commentaire) {
                return commentaire.explicationDetaillee()
                        + " Objectif de visite : " + commentaire.actionConseillee();
        }

        private static String join(String... fragments) {
                return Arrays.stream(fragments)
                        .filter(fragment -> fragment != null && !fragment.isBlank())
                        .collect(Collectors.joining(" ; ")) + ".";
        }

        private static String number(Number value) {
                return value == null ? null : String.format(Locale.FRANCE, "%.1f", value.doubleValue());
        }

        private static String percentFragment(Double value, String label) {
                return value == null ? null : label + " " + String.format(Locale.FRANCE, "%.1f%%", value);
        }

        private static String amountFragment(Integer value, String label) {
                return value == null ? null : label + " : " + value + " MAD";
        }

        private static String amountFragment(Double value, String label) {
                return value == null ? null : label + " : " + String.format(Locale.FRANCE, "%.0f MAD", value);
        }

        private static String daysFragment(Integer value, String label) {
                return value == null ? null : value + " jours " + label;
        }

        private static String segmentFragment(String segment) {
                return segment == null ? "" : ", segment " + segment;
        }

        public String genererMessageDirection(Medecin medecin, CommentaireMoteurDto commentaire) {
                String segment = medecin.getSegment() == null ? "non renseigné" : medecin.getSegment();
                String valeur = medecin.getScoreValeur() == null ? "non calculé" : String.format("%.1f/100", medecin.getScoreValeur());
                String risque = medecin.getScoreRisque() == null ? "non calculé" : String.format("%.1f/100", medecin.getScoreRisque());
                RisqueUrgence urgence = medecin.getRisqueUrgence();
                String priorite = urgence == null ? "à qualifier" : urgence.name();
                return String.format(
                                "Priorité %s, segment %s, valeur %s, risque %s. %s Les données signalent un contexte et ne démontrent pas une causalité entre visite et évolution.",
                                priorite, segment, valeur, risque, commentaire.titreSynthese());
        }
}
