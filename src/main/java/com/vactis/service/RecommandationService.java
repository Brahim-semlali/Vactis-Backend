package com.vactis.service;

import com.vactis.dto.recommandation.RecommandationItemResponse;
import com.vactis.dto.recommandation.RecommandationsGlobalResponse;
import com.vactis.model.action.Action;
import com.vactis.model.action.EtatAction;
import com.vactis.model.action.UrgenceAction;
import com.vactis.model.medecin.Medecin;
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
        List<Medecin> allMedecins = medecinRepository.findAll();

        YearMonth nowYm = YearMonth.now();
        YearMonth ymM1 = nowYm.minusMonths(1);
        YearMonth ymM2 = nowYm.minusMonths(2);
        YearMonth ymM3 = nowYm.minusMonths(3);

        Map<Long, Long> ca3DerniersMois = new HashMap<>();
        for (YearMonth ym : List.of(ymM1, ymM2, ymM3)) {
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

        List<RecommandationItemResponse> devList = new ArrayList<>();
        List<RecommandationItemResponse> reactList = new ArrayList<>();
        List<RecommandationItemResponse> irregList = new ArrayList<>();

        for (Medecin m : allMedecins) {
            Long totalCas = totalCasMap.getOrDefault(m.getId(), 0L);
            long caMoyen3Mois = ca3DerniersMois.getOrDefault(m.getId(), 0L) / 3;
            String statut = m.getStatut() != null ? m.getStatut().toUpperCase() : "INACTIF";
            String segment = m.getSegment() != null ? m.getSegment().toUpperCase() : "D";

                boolean potentielConfirme = m.getNoteInput() != null && m.getNoteInput() >= 4.0;
                boolean statutEligibleDeveloppement = Set.of("ACTIF_STABLE", "PROGRESSION", "ACTIF").contains(statut);
                boolean sousExploitationDetectee = m.getReferenceCa() != null
                    && m.getCaMensuelMoyen() != null
                    && m.getReferenceCa() - m.getCaMensuelMoyen() > 0;

                // 1. DÉVELOPPEMENT : sous-exploitation et potentiel confirmé ou note >= 4.
            if (!Boolean.TRUE.equals(m.getIsProfilIrregulier()) &&
                    ("A".equals(segment) || "B".equals(segment)) &&
                    statutEligibleDeveloppement && sousExploitationDetectee && potentielConfirme) {
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
                        .justification("Prescripteur Segment " + segment + " stable. Potentiel de consolidation et d'extension vers des analyses complexes.")
                        .pitchCommercial("Présenter les techniques de pointe du laboratoire (biologie moléculaire / immuno) et sécuriser l'exclusivité des flux.")
                        .build());
            }

                // 2. RÉACTIVATION : statut inactif et priorité moyenne ou haute.
                boolean prioriteReactivation = m.getRisqueUrgence() != null
                    && Set.of("MOYEN", "ELEVE", "URGENT").contains(m.getRisqueUrgence().name());
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
                        .justification("Prescripteur à activité cyclique validée (" + totalCas + " dossiers). Alertes de rétention neutralisées.")
                        .pitchCommercial("Synchroniser les périodes de présence (vacations chirurgicales / clinique) pour capter 100% des prélèvements lors des pics.")
                        .build());
            }
        }

        // Tri par pertinence économique
        devList.sort(Comparator.comparing(RecommandationItemResponse::getCaMoyenMensuel).reversed());
        reactList.sort(Comparator.comparing(RecommandationItemResponse::getTotalCasHistorique).reversed());
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

        Action action = new Action();
        action.setMedecin(m);
        action.setCommercial(username != null && !username.isBlank() ? username : "Commercial");
        action.setStatut(m.getStatut() != null ? m.getStatut() : "ACTIF_STABLE");
        action.setSegment(m.getSegment() != null ? m.getSegment() : "B");
        action.setLieuOrganisme(m.getOrganisme() != null ? m.getOrganisme() : "Cabinet médical");
        action.setEtatAction(EtatAction.PLANIFIEE);

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
}
