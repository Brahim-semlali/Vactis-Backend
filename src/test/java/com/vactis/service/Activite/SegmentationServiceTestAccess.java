package com.vactis.service.Activite;

import com.vactis.model.medecin.RisqueUrgence;
import com.vactis.service.PaliersControle;

/** Accès de test aux règles package-private de SegmentationService. */
public final class SegmentationServiceTestAccess {

    private SegmentationServiceTestAccess() {}

    public static RisqueUrgence niveauUrgence(PaliersControle paliers, double urgence, double risque, double silence) {
        return SegmentationService.niveauUrgence(paliers, urgence, risque, silence);
    }

    public static String fiabilite(PaliersControle paliers, long moisActifs, boolean anciennete) {
        return SegmentationService.fiabilite(paliers, moisActifs, anciennete);
    }
}
