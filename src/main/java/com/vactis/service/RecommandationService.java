package com.vactis.service;

import com.vactis.dto.recommandation.RecommandationItemResponse;
import com.vactis.dto.recommandation.RecommandationsGlobalResponse;
import com.vactis.model.action.Action;
import com.vactis.model.action.EtatAction;
import com.vactis.model.action.UrgenceAction;
import com.vactis.model.medecin.Medecin;
import com.vactis.service.Activite.ActiviteIrregulierePolicy;
import com.vactis.repository.ActionRepository;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.YearMonth;
import java.util.*;

/**
 * Service de génération des 3 listes de recommandations proactives (Section 11).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommandationService {

    private final MedecinRepository medecinRepository;
    private final ExtractionDonneesRepository extractionDonneesRepository;
    private final ActionRepository actionRepository;

    /**
     * Génère les trois listes d'opportunités commerciales :
     * 1. Développement (croissance sur Segments A/B stables)
     * 2. Réactivation (comptes clés en sommeil)
     * 3. Irréguliers rentables (optimisation des pics)
     */
    public RecommandationsGlobalResponse getRecommandations() {
        List<Medecin> allMedecins = medecinRepository.findAllWithImportedDossiers().stream()
            .filter(m -> !medecinRepository.isPseudoMedecin(m)).toList();

        List<LocalDate> datesExtraction = extractionDonneesRepository.findAllDatesDescending();
        YearMonth nowYm = datesExtraction.isEmpty()
                ? YearMonth.now()
                : YearMonth.from(datesExtraction.get(0));
        YearMonth ymM = nowYm;
        YearMonth ymM1 = ymM.minusMonths(1);
        YearMonth ymM2 = ymM.minusMonths(2);

        Map<Long, Long> ca3DerniersMois = new HashMap<>();
        for (YearMonth ym : List.of(ymM, ymM1, ymM2)) {
            List<Object[]> rows = extractionDonneesRepository.sumCaByMedecinAndDateRange(ym.atDay(1), ym.atEndOfMonth());
            for (Object[] row : rows) {
                Long mId = (Long) row[0];
                Long ca = (Long) row[1];
                ca3DerniersMois.merge(mId, ca, Long::sum);
            }
        }

        List<Object[]> totalCasRows = extractionDonneesRepository.countCasGroupedByMedecin();
        Map<Long, Long> totalCasMap = new HashMap<>();
        for (Object[] row : totalCasRows) {
            totalCasMap.put((Long) row[0], ((Number) row[1]).longValue());
        }

        Map<Long, Long> activeMonthsMap = new HashMap<>();
        for (Object[] row : extractionDonneesRepository.countMoisDistinctsGroupedByMedecin()) {
            activeMonthsMap.put((Long) row[0], ((Number) row[1]).longValue());
        }
        Map<Long, LocalDate> firstActivityMap = new HashMap<>();
        for (Object[] row : extractionDonneesRepository.findPremiereDateReceptionGroupedByMedecin()) {
            firstActivityMap.put((Long) row[0], (LocalDate) row[1]);
        }
        Map<Long, LocalDate> lastActivityMap = new HashMap<>();
        for (Object[] row : extractionDonneesRepository.findDerniereDateReceptionGroupedByMedecin()) {
            lastActivityMap.put((Long) row[0], (LocalDate) row[1]);
        }
        LocalDate dateReference = datesExtraction.isEmpty() ? nowYm.atEndOfMonth() : datesExtraction.get(0);
        Map<Long, Double> reactivationScores = new HashMap<>();
        List<Double> inactiveScores = new ArrayList<>();
        for (Medecin medecin : allMedecins) {
            if (!"INACTIF".equalsIgnoreCase(medecin.getStatut())) continue;
            LocalDate lastActivity = lastActivityMap.get(medecin.getId());
            double proximity = ReactivationPolicy.proximityScore(lastActivity, dateReference);
            double regularity = ReactivationPolicy.regularityScore(
                    activeMonthsMap.getOrDefault(medecin.getId(), 0L),
                    firstActivityMap.get(medecin.getId()), dateReference);
            double score = ReactivationPolicy.score(
                    medecin.getScoreValeur() == null ? 0.0 : medecin.getScoreValeur(), proximity, regularity);
            reactivationScores.put(medecin.getId(), score);
            inactiveScores.add(score);
        }
        Map<Long, ReactivationPolicy.Priority> reactivationPriorities = new HashMap<>();
        for (Map.Entry<Long, Double> entry : reactivationScores.entrySet()) {
            reactivationPriorities.put(entry.getKey(), ReactivationPolicy.priority(entry.getValue(), inactiveScores));
        }

        List<RecommandationItemResponse> devList = new ArrayList<>();
        List<RecommandationItemResponse> reactList = new ArrayList<>();
        List<RecommandationItemResponse> irregList = new ArrayList<>();

        for (Medecin m : allMedecins) {
            Long totalCas = totalCasMap.getOrDefault(m.getId(), 0L);
            long caMoyen3Mois = ca3DerniersMois.getOrDefault(m.getId(), 0L) / 3;
            String statut = m.getStatut() != null ? m.getStatut().toUpperCase() : "INACTIF";
            String segment = m.getSegment() != null ? m.getSegment().toUpperCase() : "D";

            double sousExploitation = RecommendationPolicy.sousExploitation(m.getReferenceCa(), m.getCaMensuelMoyen());
            boolean eligibleDeveloppement = RecommendationPolicy.eligibleDeveloppement(
                    statut,
                    Boolean.TRUE.equals(m.getIsProfilIrregulier()),
                    m.getPotentielSur100(),
                    m.getNoteInput(),
                    sousExploitation);

            if (eligibleDeveloppement) {
                devList.add(RecommandationItemResponse.builder()
                        .medecinId(m.getId())
                        .nomMedecin("Dr " + m.getNom() + " " + (m.getPrenom() != null ? m.getPrenom() : ""))
                        .specialite(m.getSpecialite())
                        .organisme(m.getOrganisme())
                        .segment(segment)
                        .statut(statut)
                        .typeRecommandation("DEVELOPPEMENT")
                        .caMoyenMensuel(caMoyen3Mois)
                        .totalCasHistorique(totalCas)
                        .noteTerrain(m.getNoteInput())
                        .potentielSur100(m.getPotentielSur100())
                        .sousExploitation(sousExploitation)
                        .scoreOpportunite((m.getScoreValeur() == null ? 0.0 : m.getScoreValeur()) * sousExploitation)
                        .scoreValeur(m.getScoreValeur())
                        .scoreRisque(m.getScoreRisque())
                        .scoreSilence(m.getScoreSilence())
                        .joursSansActivite(m.getJoursSansActivite())
                        .intervalleEffectif(m.getIntervalleEffectif())
                        .referenceCa(m.getReferenceCa())
                        .realisationObjectifPct(m.getReferenceCa() != null && m.getReferenceCa() > 0
                            ? Math.round(caMoyen3Mois * 1000.0 / m.getReferenceCa()) / 10.0 : 0.0)
                        .fiabilite(m.getFiabilite())
                        .justification("Sous-exploitation x" + String.format(java.util.Locale.FRANCE, "%.1f", sousExploitation)
                            + " et potentiel confirmé. Potentiel de consolidation à qualifier.")
                        .pitchCommercial("Présenter les techniques de pointe du laboratoire (biologie moléculaire / immuno) et sécuriser l'exclusivité des flux.")
                        .build());
            }

                // 2. RÉACTIVATION : conserver les priorités moyenne et haute du classement des inactifs.
                ReactivationPolicy.Priority priorite = reactivationPriorities.get(m.getId());
                boolean prioriteReactivation = priorite == ReactivationPolicy.Priority.HIGH
                        || priorite == ReactivationPolicy.Priority.MEDIUM;
                if ("INACTIF".equals(statut) && prioriteReactivation) {
                reactList.add(RecommandationItemResponse.builder()
                        .medecinId(m.getId())
                        .nomMedecin("Dr " + m.getNom() + " " + (m.getPrenom() != null ? m.getPrenom() : ""))
                        .specialite(m.getSpecialite())
                        .organisme(m.getOrganisme())
                        .segment(segment)
                        .statut(statut)
                        .typeRecommandation("REACTIVATION")
                        .caMoyenMensuel(caMoyen3Mois)
                        .totalCasHistorique(totalCas)
                        .noteTerrain(m.getNoteInput())
                        .potentielSur100(m.getPotentielSur100())
                        .scoreValeur(m.getScoreValeur())
                        .scoreRisque(m.getScoreRisque())
                        .scoreSilence(m.getScoreSilence())
                        .scoreReactivation(reactivationScores.get(m.getId()))
                        .prioriteReactivation(priorite.name())
                        .joursSansActivite(m.getJoursSansActivite())
                        .intervalleEffectif(m.getIntervalleEffectif())
                        .referenceCa(m.getReferenceCa())
                        .realisationObjectifPct(m.getReferenceCa() != null && m.getReferenceCa() > 0
                            ? Math.round(caMoyen3Mois * 1000.0 / m.getReferenceCa()) / 10.0 : 0.0)
                        .fiabilite(m.getFiabilite())
                        .caRecuperationEstime(m.getCaBaseline() == null ? 0L : m.getCaBaseline().longValue())
                        .ancienneteMois(ancienneteMois(m))
                        .decisionDirection("À arbitrer")
                        .justification("Compte historique significatif (" + totalCas + " dossiers cumulés) sans activité récente régulière.")
                        .pitchCommercial("Visite de reconquête : sonder les éventuels irritants logistiques ou informatiques et réapprovisionner en kits de prélèvement.")
                        .build());
            }

            // 3. IRRÉGULIERS RENTABLES : Spécialistes à activité intermittente mais à forte rentabilité unitaire
            if (Boolean.TRUE.equals(m.getIsProfilIrregulier())) {
                irregList.add(RecommandationItemResponse.builder()
                        .medecinId(m.getId())
                        .nomMedecin("Dr " + m.getNom() + " " + (m.getPrenom() != null ? m.getPrenom() : ""))
                        .specialite(m.getSpecialite())
                        .organisme(m.getOrganisme())
                        .segment(segment)
                        .statut(statut)
                        .typeRecommandation("IRREGULIER_RENTABLE")
                        .caMoyenMensuel(caMoyen3Mois)
                        .totalCasHistorique(totalCas)
                        .noteTerrain(m.getNoteInput())
                        .potentielSur100(m.getPotentielSur100())
                        .scoreValeur(m.getScoreValeur())
                        .scoreRisque(m.getScoreRisque())
                        .scoreSilence(m.getScoreSilence())
                        .tauxPresencePct(ActiviteIrregulierePolicy.tauxPresencePct(
                            activeMonthsMap.get(m.getId()), firstActivityMap.get(m.getId()), dateReference))
                        .joursSansActivite(m.getJoursSansActivite())
                        .intervalleEffectif(m.getIntervalleEffectif())
                        .referenceCa(m.getReferenceCa())
                        .realisationObjectifPct(m.getReferenceCa() != null && m.getReferenceCa() > 0
                            ? Math.round(caMoyen3Mois * 1000.0 / m.getReferenceCa()) / 10.0 : 0.0)
                        .fiabilite(m.getFiabilite())
                        .justification("Prescripteur à activité cyclique validée (" + totalCas + " dossiers). Alertes de rétention neutralisées.")
                        .pitchCommercial("Synchroniser les périodes de présence (vacations chirurgicales / clinique) pour capter 100% des prélèvements lors des pics.")
                        .build());
            }
        }

        // Tri par pertinence économique
        devList.sort(Comparator.comparing(RecommandationItemResponse::getScoreOpportunite,
            Comparator.nullsLast(Comparator.reverseOrder())));
        reactList.sort(Comparator.comparing(RecommandationItemResponse::getScoreReactivation,
            Comparator.nullsLast(Comparator.reverseOrder())));
        irregList.sort(Comparator.comparing(RecommandationItemResponse::getTotalCasHistorique).reversed());

        return RecommandationsGlobalResponse.builder()
                .developpement(devList)
                .reactivation(reactList)
                .irreguliersRentables(irregList)
                .totalOpportunites(devList.size() + reactList.size() + irregList.size())
                .build();
    }

    @Transactional
    public Action creerActionDepuisRecommandation(Long medecinId, String typeRecommandation, String username) {
        Medecin m = medecinRepository.findById(medecinId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Médecin introuvable"));
        if (medecinRepository.isPseudoMedecin(m)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Médecin introuvable");
        }

        Action action = new Action();
        action.setMedecin(m);
        action.setCommercial(username != null && !username.isBlank() ? username : "Commercial");
        action.setStatut(m.getStatut() != null ? m.getStatut() : "ACTIF_STABLE");
        action.setSegment(m.getSegment() != null ? m.getSegment() : "B");
        action.setLieuOrganisme(m.getOrganisme() != null ? m.getOrganisme() : "Cabinet médical");
        action.setEtatAction(EtatAction.PLANIFIEE);
        action.setSourceRecommandation(typeRecommandation == null ? "DEVELOPPEMENT" : typeRecommandation.toUpperCase());
        action.setDecisionRecommandation("ACCEPTEE");

        String type = typeRecommandation != null ? typeRecommandation.toUpperCase() : "DEVELOPPEMENT";
        switch (type) {
            case "REACTIVATION" -> {
                action.setActionRecommandee("Visite de réactivation - Dr " + m.getNom());
                action.setCommentaire("Reconquête ciblée d'un compte historique en sommeil (" + (m.getSpecialite() != null ? m.getSpecialite() : "Médecin") + ").");
                action.setUrgence(UrgenceAction.ELEVE);
                action.setHorizonJours(15);
                action.setDateEcheance(LocalDate.now().plusDays(15));
            }
            case "IRREGULIER_RENTABLE" -> {
                action.setActionRecommandee("Optimisation de pic d'activité - Dr " + m.getNom());
                action.setCommentaire("Calibrage et synchronisation pré-opératoire / vacation pour prescripteur cyclique.");
                action.setUrgence(UrgenceAction.MOYEN);
                action.setHorizonJours(30);
                action.setDateEcheance(LocalDate.now().plusDays(30));
            }
            default -> {
                action.setActionRecommandee("Opportunité de développement - Dr " + m.getNom());
                action.setCommentaire("Développement du volant d'analyses complémentaires pour prescripteur Segment " + (m.getSegment() != null ? m.getSegment() : "A/B") + ".");
                action.setUrgence(UrgenceAction.MOYEN);
                action.setHorizonJours(30);
                action.setDateEcheance(LocalDate.now().plusDays(30));
            }
        }

        return actionRepository.save(action);
    }

    private Long ancienneteMois(Medecin medecin) {
        if (medecin.getDatePremiereCollaboration() == null) return null;
        return ChronoUnit.MONTHS.between(medecin.getDatePremiereCollaboration(), LocalDate.now());
    }
}
