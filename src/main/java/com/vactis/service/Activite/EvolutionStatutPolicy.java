package com.vactis.service.Activite;

import java.util.Map;

/**
 * Règle R4 : évolution observée du statut d'un médecin entre le mois de la visite (M) et M+1.
 * Il s'agit d'une observation : elle ne prouve pas que la visite a causé le changement.
 */
public final class EvolutionStatutPolicy {

    public static final String FAVORABLE = "FAVORABLE";
    public static final String STABLE = "STABLE";
    public static final String DEFAVORABLE = "DEFAVORABLE";
    public static final String NON_OBSERVABLE = "NON_OBSERVABLE";
    public static final String STATUT_PAR_DEFAUT = "exclu";

    /** Table de rangs VACTIS — identique à celle du Niveau 2. Rang 1 = meilleur état, rang 8 = le plus dégradé. */
    private static final Map<String, Integer> RANG_STATUT = Map.of(
            "progression",      1,
            "actif_stable",     2,
            "surveillance",     3,
            "retention",        4,
            "silence_critique", 5,
            "onboarding",       6,
            "a_reactiver",      7,
            "exclu",            8
    );
    private static final int RANG_MAX = 8;

    private EvolutionStatutPolicy() {
    }

    /**
     * @param observable false si M+1 n'est pas clôturé ou si le médecin n'a aucune donnée en M+1
     * @return FAVORABLE, STABLE, DEFAVORABLE ou NON_OBSERVABLE
     */
    public static String evolution(String statutAvant, String statutApres, boolean observable) {
        if (!observable) return NON_OBSERVABLE;
        int rangAvant = rang(statutAvant);
        int rangApres = rang(statutApres);
        if (rangApres < rangAvant) return FAVORABLE;
        if (rangApres == rangAvant) return STABLE;
        return DEFAVORABLE;
    }

    static int rang(String statut) {
        if (statut == null) return RANG_MAX;
        return RANG_STATUT.getOrDefault(statut.trim().toLowerCase(), RANG_MAX);
    }
}
