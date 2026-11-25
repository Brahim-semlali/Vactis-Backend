package com.vactis.service;

import com.vactis.model.Controle.TypeControle;

import java.util.Set;

public final class SilenceAlertPolicy {

    public enum NiveauRupture { AUCUNE, SURVEILLANCE, CRITIQUE }

    private static final Set<String> STATUTS_ELIGIBLES = Set.of(
            "ACTIF", "ACTIF_STABLE", "PROGRESSION", "SURVEILLANCE", "RETENTION");

    private SilenceAlertPolicy() {}

    public static boolean rythmeRecentFiable(long totalCas, long moisActifs, long datesDistinctes) {
        return totalCas >= 5 || moisActifs >= 2 || datesDistinctes >= 3;
    }

    public static NiveauRupture niveauRupture(double ratio) {
        if (ratio >= 2.0) return NiveauRupture.CRITIQUE;
        if (ratio >= 1.5) return NiveauRupture.SURVEILLANCE;
        return NiveauRupture.AUCUNE;
    }

    /** Niveau de rupture selon les paliers RUPTURE de la table Contrôle (ratio exprimé en %). */
    public static NiveauRupture niveauRupture(double ratio, PaliersControle paliers) {
        String etat = PaliersControle.ou(paliers, TypeControle.RUPTURE).etatPour(ratio * 100.0);
        try {
            return etat == null ? niveauRupture(ratio) : NiveauRupture.valueOf(etat);
        } catch (IllegalArgumentException e) {
            return niveauRupture(ratio);
        }
    }

    public static double scoreUrgence(double ratio, double scoreRisque, double scoreValeur) {
        double scoreSilence = Math.min(100.0, Math.max(0.0, (ratio - 1.0) * 100.0));
        return Math.round(Math.min(100.0, 0.40 * scoreSilence + 0.30 * scoreRisque + 0.30 * scoreValeur) * 10.0) / 10.0;
    }

    public static boolean alerteStrategiqueEligible(
            String statut,
            String segment,
            Double notePotentiel,
            boolean fiabilitePartielleOuFiable,
            boolean impactMoyenOuFort,
            double ratioSilence,
            boolean profilIrregulier
    ) {
        return alerteStrategiqueEligible(statut, segment, notePotentiel, fiabilitePartielleOuFiable,
                impactMoyenOuFort, niveauRupture(ratioSilence), profilIrregulier);
    }

    public static boolean alerteStrategiqueEligible(
            String statut,
            String segment,
            Double notePotentiel,
            boolean fiabilitePartielleOuFiable,
            boolean impactMoyenOuFort,
            NiveauRupture niveauRupture,
            boolean profilIrregulier
    ) {
        String statutNormalise = statut == null ? "" : statut.trim().toUpperCase();
        String segmentNormalise = segment == null ? "" : segment.trim().toUpperCase();
        boolean strategique = "A".equals(segmentNormalise) || "B".equals(segmentNormalise);
        boolean statutAdmis = STATUTS_ELIGIBLES.contains(statutNormalise)
                || (profilIrregulier && strategique && "ACTIVITE_IRREGULIERE".equals(statutNormalise));
        return statutAdmis && strategique
                && notePotentiel != null && notePotentiel >= 4.0
                && fiabilitePartielleOuFiable
                && impactMoyenOuFort
                && niveauRupture == NiveauRupture.CRITIQUE;
    }
}