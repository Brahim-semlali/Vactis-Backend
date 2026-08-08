package com.vactis.service;

import com.vactis.dto.activite.ActionsVactisResponse;
import com.vactis.dto.activite.CompteRenduTerrainResponse;
import com.vactis.model.medecin.QualificationVisite;
import com.vactis.model.medecin.RetourTerrain;
import com.vactis.model.medecin.StatutVisite;
import com.vactis.repository.ActionRepository;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.RetourTerrainRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service métier pour l'exécution terrain (Niveau 3 du module Lecture activité).
 * Calcule les indicateurs "Actions VACTIS" et "Compte-rendu terrain du mois" à partir de RetourTerrain.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActiviteTerrainService {

    private final RetourTerrainRepository retourTerrainRepository;
    private final ActionRepository actionRepository;
    private final ExtractionDonneesRepository extractionDonneesRepository;

    private static final DateTimeFormatter YYYY_MM = DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * Calcule le bloc 1 : "Lecture réalisation commerciale / actions VACTIS".
     */
    public ActionsVactisResponse getActionsVactis(String moisParam) {
        YearMonth ym = parseOrGetDefaultMois(moisParam);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        List<RetourTerrain> retours = retourTerrainRepository.findByDateVisiteBetween(start, end);
        Long actionsCount = actionRepository.countAllActions();
        long actionsGenerees = actionsCount != null ? actionsCount : 0L;

        long renseignees = retours.size();
        long realisees = retours.stream().filter(r -> getStatutSafe(r) == StatutVisite.REALISEE).count();
        long nonRealisees = retours.stream().filter(r -> getStatutSafe(r) == StatutVisite.NON_REALISEE).count();

        double tauxTerrain = renseignees > 0 ? Math.round((realisees * 100.0 / renseignees) * 10.0) / 10.0 : 0.0;

        // Groupement par visiteur/commercial
        Map<String, List<RetourTerrain>> byCommercial = retours.stream()
                .collect(Collectors.groupingBy(r -> (r.getVisiteur() == null || r.getVisiteur().isBlank()) ? "Non renseigné" : r.getVisiteur()));

        List<ActionsVactisResponse.RepartitionCommercial> repartition = byCommercial.entrySet().stream()
                .map(e -> {
                    String comm = e.getKey();
                    List<RetourTerrain> list = e.getValue();
                    long cRenseignees = list.size();
                    long cRealisees = list.stream().filter(r -> getStatutSafe(r) == StatutVisite.REALISEE).count();
                    long cNonRealisees = list.stream().filter(r -> getStatutSafe(r) == StatutVisite.NON_REALISEE).count();
                    return ActionsVactisResponse.RepartitionCommercial.builder()
                            .commercial(comm)
                            .renseignees(cRenseignees)
                            .realisees(cRealisees)
                            .nonRealisees(cNonRealisees)
                            .build();
                })
                .sorted(Comparator.comparing(ActionsVactisResponse.RepartitionCommercial::getRenseignees).reversed())
                .collect(Collectors.toList());

        return ActionsVactisResponse.builder()
                .mois(ym.format(YYYY_MM))
                .actionsGenerees(actionsGenerees)
                .visitesRenseignees(renseignees)
                .visitesRealisees(realisees)
                .nonRealisees(nonRealisees)
                .tauxTerrain(tauxTerrain)
                .repartitionParCommercial(repartition)
                .build();
    }

    /**
     * Calcule le bloc 2 : "Compte-rendu terrain du mois".
     */
    public CompteRenduTerrainResponse getCompteRenduTerrain(String moisParam) {
        YearMonth ym = parseOrGetDefaultMois(moisParam);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        List<RetourTerrain> retours = retourTerrainRepository.findByDateVisiteBetween(start, end);

        long renseignees = retours.size();
        long realisees = retours.stream().filter(r -> getStatutSafe(r) == StatutVisite.REALISEE).count();
        long nonRealisees = retours.stream().filter(r -> getStatutSafe(r) == StatutVisite.NON_REALISEE).count();
        long statutNonRenseigne = retours.stream().filter(r -> getStatutSafe(r) == StatutVisite.NON_RENSEIGNE).count();

        double tauxTerrain = renseignees > 0 ? Math.round((realisees * 100.0 / renseignees) * 10.0) / 10.0 : 0.0;

        // 5 cartes cliquables
        long reclamations = retours.stream().filter(r -> Boolean.TRUE.equals(r.getReclamation())).count();
        long defavorables = retours.stream().filter(r -> getQualifSafe(r) == QualificationVisite.DEFAVORABLE).count();
        long favorables = retours.stream().filter(r -> getQualifSafe(r) == QualificationVisite.FAVORABLE).count();

        // Groupement par commercial détaillé
        Map<String, List<RetourTerrain>> byCommercial = retours.stream()
                .collect(Collectors.groupingBy(r -> (r.getVisiteur() == null || r.getVisiteur().isBlank()) ? "Non renseigné" : r.getVisiteur()));

        List<CompteRenduTerrainResponse.RepartitionCommercialDetail> repartition = byCommercial.entrySet().stream()
                .map(e -> {
                    String comm = e.getKey();
                    List<RetourTerrain> list = e.getValue();
                    long cRenseignees = list.size();
                    long cRealisees = list.stream().filter(r -> getStatutSafe(r) == StatutVisite.REALISEE).count();
                    long cReclamations = list.stream().filter(r -> Boolean.TRUE.equals(r.getReclamation())).count();
                    long cFavorables = list.stream().filter(r -> getQualifSafe(r) == QualificationVisite.FAVORABLE).count();

                    return CompteRenduTerrainResponse.RepartitionCommercialDetail.builder()
                            .commercial(comm)
                            .renseignees(cRenseignees)
                            .realisees(cRealisees)
                            .reclamations(cReclamations)
                            .favorables(cFavorables)
                            .build();
                })
                .sorted(Comparator.comparing(CompteRenduTerrainResponse.RepartitionCommercialDetail::getRenseignees).reversed())
                .collect(Collectors.toList());

        List<CompteRenduTerrainResponse.RetourTerrainDetail> retoursDetail = retours.stream()
                .map(r -> CompteRenduTerrainResponse.RetourTerrainDetail.builder()
                        .id(r.getId())
                        .medecinId(r.getMedecin() != null ? r.getMedecin().getId() : null)
                        .codeMedecin(r.getMedecin() != null ? r.getMedecin().getCodeMedecin() : null)
                        .nomMedecin((r.getNomMedecin() != null && !r.getNomMedecin().isBlank())
                                ? r.getNomMedecin()
                                : (r.getMedecin() != null
                                        ? ((r.getMedecin().getNom() != null ? r.getMedecin().getNom() : "") + " " + (r.getMedecin().getPrenom() != null ? r.getMedecin().getPrenom() : "")).trim()
                                        : "Non renseigné"))
                        .specialite(r.getMedecin() != null ? r.getMedecin().getSpecialite() : null)
                        .visiteur((r.getVisiteur() != null && !r.getVisiteur().isBlank()) ? r.getVisiteur() : "Non renseigné")
                        .dateVisite(r.getDateVisite())
                        .note(r.getNote())
                        .commentaire(r.getCommentaire())
                        .statutVisite(getStatutSafe(r).name())
                        .qualification(getQualifSafe(r).name())
                        .reclamation(Boolean.TRUE.equals(r.getReclamation()))
                        .build())
                .collect(Collectors.toList());

        return CompteRenduTerrainResponse.builder()
                .mois(ym.format(YYYY_MM))
                .visitesRenseignees(renseignees)
                .visitesRealisees(realisees)
                .visitesNonRealisees(nonRealisees)
                .statutNonRenseigne(statutNonRenseigne)
                .tauxTerrain(tauxTerrain)
                .visitesAvecReclamation(reclamations)
                .defavorablesRefus(defavorables)
                .nonRealisees(nonRealisees)
                .statutNonRenseigneCarte(statutNonRenseigne)
                .favorables(favorables)
                .repartitionParCommercial(repartition)
                .retours(retoursDetail)
                .build();
    }

    private StatutVisite getStatutSafe(RetourTerrain r) {
        return r.getStatutVisite() != null ? r.getStatutVisite() : StatutVisite.REALISEE;
    }

    private QualificationVisite getQualifSafe(RetourTerrain r) {
        return r.getQualification() != null ? r.getQualification() : QualificationVisite.NON_RENSEIGNE;
    }

    private YearMonth parseOrGetDefaultMois(String moisParam) {
        if (moisParam != null && !moisParam.isBlank()) {
            try {
                return YearMonth.parse(moisParam.trim(), YYYY_MM);
            } catch (Exception e) {
                log.warn("Format de mois invalide: {}, fallback au mois disponible ou actuel.", moisParam);
            }
        }
        List<LocalDate> dates = extractionDonneesRepository.findAllDatesDescending();
        return dates.isEmpty() ? YearMonth.now() : YearMonth.from(dates.get(0));
    }
}
