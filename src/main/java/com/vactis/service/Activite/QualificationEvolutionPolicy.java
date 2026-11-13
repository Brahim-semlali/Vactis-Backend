package com.vactis.service.Activite;

import com.vactis.model.medecin.QualificationVisite;

public final class QualificationEvolutionPolicy {

    public record Resultat(String diagnostic, boolean concordant) {}

    private QualificationEvolutionPolicy() {}

    public static Resultat comparer(QualificationVisite qualification, String evolution, String statutApres) {
        if ("NON_OBSERVABLE".equals(evolution)) return new Resultat("NON_OBSERVABLE", false);
        if (qualification == QualificationVisite.FAVORABLE) {
            if ("FAVORABLE".equals(evolution)) return new Resultat("SUCCES_CONFIRME", true);
            if ("RETENTION".equalsIgnoreCase(statutApres)) return new Resultat("FAUX_POSITIF", false);
            return new Resultat("QUALIFICATION_NON_CONFIRMEE", false);
        }
        if (qualification == QualificationVisite.DEFAVORABLE || qualification == QualificationVisite.RECLAMATION) {
            if ("DEFAVORABLE".equals(evolution)) return new Resultat("PERTE_CONFIRMEE", true);
            if ("FAVORABLE".equals(evolution)) return new Resultat("RESILIENCE_INATTENDUE", false);
            return new Resultat("SITUATION_STABLE", false);
        }
        if (qualification == QualificationVisite.NEUTRE && "STABLE".equals(evolution)) {
            return new Resultat("STABLE", true);
        }
        return new Resultat("QUALIFICATION_NON_CONFIRMEE", false);
    }
}