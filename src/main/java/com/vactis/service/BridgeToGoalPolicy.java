package com.vactis.service;

public final class BridgeToGoalPolicy {

    private BridgeToGoalPolicy() {}

    public static long runRateAnnuel(long caYtd, int moisEcoules) {
        return moisEcoules > 0 ? Math.round((double) caYtd * 12.0 / moisEcoules) : 0L;
    }

    public static long gapProjete(long objectifAnnuel, long runRateAnnuel) {
        return Math.max(0L, objectifAnnuel - runRateAnnuel);
    }

    public static long effortMensuel(long gap, int moisRestants) {
        return moisRestants > 0 ? Math.round((double) gap / moisRestants) : gap;
    }

    public static double couverturePourcent(long potentielInterne, long gapProjete) {
        if (gapProjete <= 0) return 100.0;
        return Math.round((Math.min(Math.max(potentielInterne, 0L), gapProjete) * 1000.0) / gapProjete) / 10.0;
    }

    public static String decisionCouverture(double couverturePourcent) {
        if (couverturePourcent >= 100.0) return "PORTEFEUILLE_SUFFISANT";
        if (couverturePourcent >= 50.0) return "PROSPECTION_CIBLEE";
        if (couverturePourcent >= 25.0) return "PROSPECTION_INDISPENSABLE";
        return "ACQUISITION_NECESSAIRE";
    }

    public static long potentielActivable(long potentielTheorique, double tauxActivation) {
        return Math.round(Math.max(0L, potentielTheorique) * Math.max(0.0, Math.min(tauxActivation, 1.0)));
    }

    public static int nouveauxMedecinsNecessaires(long gapRestant, long caMensuelMoyen, double monteeEnCharge) {
        long caAttendu = Math.round(Math.max(0L, caMensuelMoyen) * Math.max(0.0, Math.min(monteeEnCharge, 1.0)));
        return gapRestant > 0 && caAttendu > 0 ? (int) Math.ceil((double) gapRestant / caAttendu) : 0;
    }
}