package com.vactis.service;

import com.vactis.model.Controle.TypeControle;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.RetourTerrain;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.RetourTerrainRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SegmentationService {

    private final MedecinRepository medecinRepository;
    private final ExtractionDonneesRepository extractionDonneesRepository;
    private final RetourTerrainRepository retourTerrainRepository;
    private final ControleService controleService;

    @Transactional
    public void recalculerSegmentationPortefeuille() {
        List<Medecin> medecins = medecinRepository.findAll();
        if (medecins.isEmpty()) return;

        int totalMedecins = medecins.size();

        // 1. Récupérer le nombre de cas par médecin
        List<Object[]> casGrouped = extractionDonneesRepository.countCasGroupedByMedecin();
        Map<Long, Long> casMap = new HashMap<>();
        for (Object[] row : casGrouped) {
            Long medId = (Long) row[0];
            Long count = ((Number) row[1]).longValue();
            casMap.put(medId, count);
        }

        // 2. Calculer le CA max et le Nombre de cas max dans le portefeuille pour la normalisation
        double maxCa = medecins.stream()
                .mapToDouble(m -> m.getCaMois() != null ? m.getCaMois().doubleValue() : 0.0)
                .max()
                .orElse(0.0);
        if (maxCa <= 0) maxCa = 1.0;

        double maxCas = medecins.stream()
                .mapToDouble(m -> casMap.getOrDefault(m.getId(), 0L).doubleValue())
                .max()
                .orElse(0.0);
        if (maxCas <= 0) maxCas = 1.0;

        // 3. Trier par CA mensuel croissant pour déterminer le rang percentile (Performance)
        List<Medecin> medecinsTriesParCa = new ArrayList<>(medecins);
        medecinsTriesParCa.sort(Comparator.comparingDouble(m -> m.getCaMois() != null ? m.getCaMois().doubleValue() : 0.0));

        Map<Long, Double> performanceMap = new HashMap<>();
        for (int i = 0; i < totalMedecins; i++) {
            Medecin m = medecinsTriesParCa.get(i);
            double performance = (totalMedecins == 1) ? 100.0 : ((double) i / (totalMedecins - 1)) * 100.0;
            performanceMap.put(m.getId(), performance);
        }

        // 4. Calculer le Score de valeur et attribuer le segment pour chaque médecin
        for (Medecin m : medecins) {
            // A. Potentiel (40%) : Note Terrain prioritaire > Note Input > Note neutre 3.0/5.0
            double noteSur5 = 3.0;
            Optional<RetourTerrain> dernierRetour = retourTerrainRepository.findFirstByMedecinOrderByDateVisiteDescCreatedAtDesc(m);
            if (dernierRetour.isPresent() && dernierRetour.get().getNote() != null) {
                noteSur5 = dernierRetour.get().getNote();
            } else if (m.getNoteInput() != null) {
                noteSur5 = m.getNoteInput();
            }
            double potentielSur100 = Math.min(100.0, Math.max(0.0, (noteSur5 / 5.0) * 100.0));

            // B. Performance (40%) : Rang percentile CA dans le portefeuille
            double performance = performanceMap.getOrDefault(m.getId(), 0.0);

            // C. Poids économique (20%) : 50% CA normalisé + 50% Cas normalisé
            double caPhys = m.getCaMois() != null ? m.getCaMois().doubleValue() : 0.0;
            double casPhys = casMap.getOrDefault(m.getId(), 0L).doubleValue();

            double caNormalise = (caPhys / maxCa) * 100.0;
            double casNormalise = (casPhys / maxCas) * 100.0;
            double poidsEconomique = (0.50 * caNormalise) + (0.50 * casNormalise);

            // D. Score de valeur global
            double scoreValeur = (0.40 * potentielSur100) + (0.40 * performance) + (0.20 * poidsEconomique);
            scoreValeur = Math.round(scoreValeur * 100.0) / 100.0; // Arrondi à 2 décimales

            // E. Détermination du segment (via règles de la table controle ou fallback A/B/C/D)
            String segment = controleService.determinerEtatParScore(TypeControle.SEGEMENTS, scoreValeur);
            if (segment == null || segment.isBlank()) {
                if (scoreValeur >= 75.0) segment = "A";
                else if (scoreValeur >= 60.0) segment = "B";
                else if (scoreValeur >= 45.0) segment = "C";
                else segment = "D";
            }

            m.setScoreValeur(scoreValeur);
            m.setSegment(segment);
        }

        medecinRepository.saveAll(medecins);
        log.info("Recalcul de la segmentation Anapath terminé pour {} médecins.", totalMedecins);
    }
}
