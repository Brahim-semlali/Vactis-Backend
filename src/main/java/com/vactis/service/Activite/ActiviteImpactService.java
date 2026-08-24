package com.vactis.service.Activite;

import com.vactis.dto.activite.DetailEvolutionResponse;
import com.vactis.dto.activite.EvolutionParCommercialResponse;
import com.vactis.dto.activite.RapportImpactResponse;
import com.vactis.model.action.Action;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.QualificationVisite;
import com.vactis.model.medecin.RetourTerrain;
import com.vactis.model.medecin.StatutVisite;
import com.vactis.model.medecin.TypeVisite;
import com.vactis.repository.ActionRepository;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
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
 * Service métier pour le Niveau 4 du module "Lecture activité" — Impact des visites terrain.
 *
 * Réutilise {@link ActivitePortefeuilleService#buildStatutMapForMonth} pour calculer le statut
 * VACTIS avant/après chaque visite, selon les règles métier R3/R4 définies dans le plan.
 *
 * Trois blocs :
 *  1. getRapportImpact        → compteurs Vue globale + Exécution actions
 *  2. getEvolutionParCommercial → graphique empilé + classification avant/après
 *  3. getDetailEvolution      → tableau détaillé paginé
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActiviteImpactService {

    private final RetourTerrainRepository retourTerrainRepository;
    private final ActionRepository actionRepository;
    private final MedecinRepository medecinRepository;
    private final ExtractionDonneesRepository extractionDonneesRepository;
    private final ActivitePortefeuilleService portefeuilleService;

    private static final DateTimeFormatter YYYY_MM = DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * Table de rangs VACTIS — identique à celle du Niveau 2.
     * Rang 1 = meilleur état, rang 8 = état le plus dégradé.
     */
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

    // ──────────────────────────────────────────────────────────────────────────
    // Bloc 1 — Rapport d'impact
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Calcule les compteurs du bloc 1 : Vue globale + Exécution des actions VACTIS.
     * Périmètre : tous les retours terrain dont la date_visite appartient au mois fourni.
     */
    public RapportImpactResponse getRapportImpact(String moisParam) {
        YearMonth ym = parseOrDefault(moisParam);
        LocalDate start = ym.atDay(1);
        LocalDate end   = ym.atEndOfMonth();

        // Chargement en une seule requête avec fetch sur action + medecin (évite N+1)
        List<RetourTerrain> retours = retourTerrainRepository.findByDateVisiteBetweenWithFetch(start, end);
        enrichAndLinkVactisActions(retours);

        // ── Vue globale ──────────────────────────────────────────────────────
        long totalRenseignees      = retours.size();
        long totalRealisees        = retours.stream().filter(this::isRealisee).count();
        long vactisRealisees       = retours.stream().filter(r -> isVactis(r) && isRealisee(r)).count();
        long horsVactisRealisees   = retours.stream().filter(r -> !isVactis(r) && isRealisee(r)).count();
        long avecReclamation       = retours.stream().filter(r -> Boolean.TRUE.equals(r.getReclamation())).count();
        long favorables            = retours.stream().filter(r -> qualif(r) == QualificationVisite.FAVORABLE).count();
        long defavorables          = retours.stream().filter(r -> qualif(r) == QualificationVisite.DEFAVORABLE).count();
        // R6 décision : NEUTRE et NON_RENSEIGNE sont regroupés dans "sans qualification"
        long sansQualification     = retours.stream().filter(r ->
                qualif(r) == QualificationVisite.NON_RENSEIGNE || qualif(r) == QualificationVisite.NEUTRE).count();

        // ── Exécution des actions VACTIS ─────────────────────────────────────
        long actionsGenerees  = Optional.ofNullable(actionRepository.countByCycleMensuel(ym.format(YYYY_MM))).orElse(0L);
        long excluesDirection = Optional.ofNullable(actionRepository.countActionsExcluesDirection()).orElse(0L);
        long vactisRenseignees   = retours.stream().filter(this::isVactis).count();
        long vactisNonRealisees  = retours.stream().filter(r -> isVactis(r) && isNonRealisee(r)).count();
        long sanRetourTerrain    = Math.max(0, actionsGenerees - vactisRenseignees);

        long denominateur = actionsGenerees - excluesDirection;
        double tauxRealisation = denominateur > 0
                ? Math.round((vactisRealisees * 100.0 / denominateur) * 10.0) / 10.0
                : 0.0;

        return RapportImpactResponse.builder()
                .mois(ym.format(YYYY_MM))
                .totalVisitesRenseignees(totalRenseignees)
                .totalVisitesRealisees(totalRealisees)
                .visitesVactisRealisees(vactisRealisees)
                .visitesHorsVactisRealisees(horsVactisRealisees)
                .visitesAvecReclamation(avecReclamation)
                .visitesFavorables(favorables)
                .visitesDefavorables(defavorables)
                .visitesSansQualification(sansQualification)
                .actionsVactisGenerees(actionsGenerees)
                .vactisRealisees(vactisRealisees)
                .vactisRenseignees(vactisRenseignees)
                .vactisNonRealisees(vactisNonRealisees)
                .sanRetourTerrain(sanRetourTerrain)
                .excluesDirection(excluesDirection)
                .tauxRealisation(tauxRealisation)
                .build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Bloc 2 — Évolution par commercial
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Calcule le graphique empilé et la classification Favorable/Stable/Défavorable/Non observable.
     * Le graphique porte sur TOUTES les visites réalisées (VACTIS + hors VACTIS).
     * La classification porte uniquement sur les visites VACTIS (action IS NOT NULL ou liée).
     */
    public EvolutionParCommercialResponse getEvolutionParCommercial(String moisParam) {
        YearMonth ym    = parseOrDefault(moisParam);
        LocalDate start = ym.atDay(1);
        LocalDate end   = ym.atEndOfMonth();

        List<RetourTerrain> tousRetours = retourTerrainRepository.findByDateVisiteBetweenWithFetch(start, end);
        enrichAndLinkVactisActions(tousRetours);
        List<Medecin> medecins = medecinRepository.findAll();

        // Calcul des statuts avant (mois M) et après (mois M+1)
        Map<Long, String> statutsM  = portefeuilleService.buildStatutMapForMonth(ym, medecins);
        Map<Long, String> statutsM1 = portefeuilleService.buildStatutMapForMonth(ym.plusMonths(1), medecins);
        boolean apresCalculable = isApresCalculable(ym);

        // ── Graphique empilé : TOUTES visites réalisées par commercial ────────
        List<RetourTerrain> realisees = tousRetours.stream()
                .filter(this::isRealisee)
                .collect(Collectors.toList());

        Map<String, List<RetourTerrain>> byCommercial = realisees.stream()
                .collect(Collectors.groupingBy(this::commercialSafe));

        List<EvolutionParCommercialResponse.RepartitionCommercial> repartition = byCommercial.entrySet().stream()
                .map(e -> {
                    String comm = e.getKey();
                    List<RetourTerrain> list = e.getValue();
                    Map<String, Long> parType = Arrays.stream(TypeVisite.values())
                            .collect(Collectors.toMap(
                                    TypeVisite::name,
                                    tv -> list.stream().filter(r -> typeVisiteSafe(r) == tv).count(),
                                    (a, b) -> a,
                                    LinkedHashMap::new
                            ));
                    return EvolutionParCommercialResponse.RepartitionCommercial.builder()
                            .commercial(comm)
                            .totalRealisees(list.size())
                            .parTypeVisite(parType)
                            .build();
                })
                .sorted(Comparator.comparingLong(EvolutionParCommercialResponse.RepartitionCommercial::getTotalRealisees).reversed())
                .collect(Collectors.toList());

        // ── Classification : uniquement visites VACTIS ────────────────────────
        List<RetourTerrain> vactisRetours = tousRetours.stream()
                .filter(this::isVactis)
                .collect(Collectors.toList());

        // Compteurs globaux
        long totalAnalyse  = 0, gFavorable = 0, gStable = 0, gDefavorable = 0, gNonObservable = 0;

        // Compteurs par commercial
        Map<String, long[]> evolutionByComm = new LinkedHashMap<>(); // [total, fav, stable, def, nonObs]
        for (RetourTerrain r : vactisRetours) {
            if (!isRealisee(r)) continue; // on n'analyse que les réalisées
            Long medecinId = r.getMedecin().getId();
            String evo = calculerEvolution(medecinId, ym, statutsM, statutsM1, apresCalculable);
            totalAnalyse++;
            String comm = commercialSafe(r);
            long[] c = evolutionByComm.computeIfAbsent(comm, k -> new long[5]);
            c[0]++;
            switch (evo) {
                case "FAVORABLE"      -> { gFavorable++;    c[1]++; }
                case "STABLE"         -> { gStable++;       c[2]++; }
                case "DEFAVORABLE"    -> { gDefavorable++;  c[3]++; }
                case "NON_OBSERVABLE" -> { gNonObservable++; c[4]++; }
            }
        }

        List<EvolutionParCommercialResponse.EvolutionCommercial> evolutionList = evolutionByComm.entrySet().stream()
                .map(e -> {
                    String comm = e.getKey();
                    long[] c = e.getValue();
                    long mesurables = c[0] - c[4]; // total - non observables
                    double taux = mesurables > 0 ? Math.round((c[1] * 100.0 / mesurables) * 10.0) / 10.0 : 0.0;
                    return EvolutionParCommercialResponse.EvolutionCommercial.builder()
                            .commercial(comm)
                            .totalAnalyse(c[0])
                            .favorable(c[1])
                            .stable(c[2])
                            .defavorable(c[3])
                            .nonObservable(c[4])
                            .tauxFavorable(taux)
                            .build();
                })
                .sorted(Comparator.comparingLong(EvolutionParCommercialResponse.EvolutionCommercial::getTotalAnalyse).reversed())
                .collect(Collectors.toList());

        return EvolutionParCommercialResponse.builder()
                .mois(ym.format(YYYY_MM))
                .visitesParCommercial(repartition)
                .totalAnalyse(totalAnalyse)
                .favorable(gFavorable)
                .stable(gStable)
                .defavorable(gDefavorable)
                .nonObservable(gNonObservable)
                .evolutionParCommercial(evolutionList)
                .build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Bloc 3 — Tableau détaillé paginé
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Retourne le tableau détaillé post-visite VACTIS, paginé côté serveur.
     * Portée : retours terrain avec action IS NOT NULL (ou associée) sur le mois.
     *
     * @param moisParam mois au format YYYY-MM
     * @param page      numéro de page 0-indexé
     * @param taille    nombre de lignes par page (défaut 20)
     */
    public DetailEvolutionResponse getDetailEvolution(String moisParam, int page, int taille) {
        YearMonth ym    = parseOrDefault(moisParam);
        LocalDate start = ym.atDay(1);
        LocalDate end   = ym.atEndOfMonth();

        // Récupérer et enrichir tous les retours du mois avec fetch
        List<RetourTerrain> tousRetours = retourTerrainRepository.findByDateVisiteBetweenWithFetch(start, end);
        enrichAndLinkVactisActions(tousRetours);

        List<RetourTerrain> vactisRetours = tousRetours.stream()
                .filter(this::isVactis)
                .collect(Collectors.toList());

        List<Medecin> medecins = medecinRepository.findAll();

        // Calcul batch des statuts M et M+1
        Map<Long, String> statutsM  = portefeuilleService.buildStatutMapForMonth(ym, medecins);
        Map<Long, String> statutsM1 = portefeuilleService.buildStatutMapForMonth(ym.plusMonths(1), medecins);
        // M+1 est calculable si le mois M+1 dispose de données d'extraction en base
        boolean apresCalculable = isApresCalculable(ym);

        long totalLignes = vactisRetours.size();
        int totalPages   = taille > 0 ? (int) Math.ceil((double) totalLignes / taille) : 1;

        // Pagination en mémoire
        int fromIdx = Math.min(page * taille, (int) totalLignes);
        int toIdx   = Math.min(fromIdx + taille, (int) totalLignes);
        List<RetourTerrain> page_ = vactisRetours.subList(fromIdx, toIdx);

        List<DetailEvolutionResponse.LigneDetail> lignes = page_.stream()
                .map(r -> {
                    Long medecinId = r.getMedecin().getId();
                    String statutAvant = statutsM.getOrDefault(medecinId, "exclu");
                    String evo        = calculerEvolution(medecinId, ym, statutsM, statutsM1, apresCalculable);
                    String statutApres = evo.equals("NON_OBSERVABLE")
                            ? "non_observable"
                            : statutsM1.getOrDefault(medecinId, "exclu");

                    String tv = typeVisiteSafe(r).name().toLowerCase();
                    // Format type action / visite
                    String actionRec = (r.getAction() != null && r.getAction().getActionRecommandee() != null)
                            ? r.getAction().getActionRecommandee().toLowerCase().replace(" ", "_")
                            : "visite_" + tv + "_" + statutAvant;
                    String typeActionVisite = actionRec;

                    String nomMedecin = r.getNomMedecin() != null && !r.getNomMedecin().isBlank()
                            ? r.getNomMedecin()
                            : buildNom(r.getMedecin());

                    return DetailEvolutionResponse.LigneDetail.builder()
                            .retourTerrainId(r.getId())
                            .nomMedecin(nomMedecin)
                            .commercial(commercialSafe(r))
                            .typeActionVisite(typeActionVisite)
                            .typeVisite(tv)
                            .statutAvant(statutAvant)
                            .qualification(qualif(r).name().toLowerCase())
                            .statutApres(statutApres)
                            .evolution(evo)
                            .commentaire(r.getCommentaire())
                            .dateVisite(r.getDateVisite())
                            .build();
                })
                .collect(Collectors.toList());

        return DetailEvolutionResponse.builder()
                .mois(ym.format(YYYY_MM))
                .page(page)
                .taille(taille)
                .totalLignes(totalLignes)
                .totalPages(totalPages)
                .lignes(lignes)
                .build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Méthodes privées utilitaires
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Pour les retours terrain où r.action est null (ex. créés manuellement ou historiques),
     * cette méthode tente d'associer l'Action existante du médecin pour compléter les informations
     * (action_id et type_visite).
     */
    private void enrichAndLinkVactisActions(List<RetourTerrain> retours) {
        if (retours.isEmpty()) return;

        Map<Long, Action> actionByMedecinId = actionRepository.findAll().stream()
                .filter(a -> a.getMedecin() != null)
                .collect(Collectors.toMap(
                        a -> a.getMedecin().getId(),
                        a -> a,
                        (a1, a2) -> a1
                ));

        for (RetourTerrain r : retours) {
            if (r.getMedecin() == null) continue;
            Long medId = r.getMedecin().getId();
            Action action = r.getAction() != null ? r.getAction() : actionByMedecinId.get(medId);

            if (r.getAction() == null && action != null) {
                r.setAction(action);
            }

            if (r.getTypeVisite() == null || r.getTypeVisite() == TypeVisite.AUTRE) {
                r.setTypeVisite(deduceTypeVisite(r, action));
            }
        }
    }

    private TypeVisite deduceTypeVisite(RetourTerrain r, Action a) {
        if (r.getTypeVisite() != null && r.getTypeVisite() != TypeVisite.AUTRE) {
            return r.getTypeVisite();
        }
        String actionRec = (a != null && a.getActionRecommandee() != null) ? a.getActionRecommandee().toLowerCase() : "";
        if (actionRec.contains("fidelisation") || actionRec.contains("progression")) return TypeVisite.FIDELISATION;
        if (actionRec.contains("retention")) return TypeVisite.RETENTION;
        if (actionRec.contains("prospect") || actionRec.contains("onboarding")) return TypeVisite.PROSPECTION;
        if (actionRec.contains("diagnostic") || actionRec.contains("surveillance")) return TypeVisite.DIAGNOSTIC;
        if (actionRec.contains("reconnaissance")) return TypeVisite.RECONNAISSANCE;
        if (actionRec.contains("silence") || actionRec.contains("urgence")) return TypeVisite.URGENCE_SILENCE;

        if (r.getMedecin() != null && r.getMedecin().getStatutPilotage() != null) {
            switch (r.getMedecin().getStatutPilotage()) {
                case PROGRESSION -> { return TypeVisite.FIDELISATION; }
                case SURVEILLANCE -> { return TypeVisite.DIAGNOSTIC; }
                case SILENCE_CRITIQUE -> { return TypeVisite.URGENCE_SILENCE; }
                case ONBOARDING -> { return TypeVisite.PROSPECTION; }
                default -> {}
            }
        }
        return TypeVisite.AUTRE;
    }

    /**
     * Calcule l'évolution post-visite selon la règle R4 :
     *  - rang(après) < rang(avant) → FAVORABLE
     *  - rang(après) == rang(avant) → STABLE
     *  - rang(après) > rang(avant) → DEFAVORABLE
     *  - M+1 non calculable → NON_OBSERVABLE
     *
     * apresCalculable est true uniquement si le mois M+1 dispose de données
     * d'extraction disponibles (c'est-à-dire si M+1 ≤ dernier mois clôturé).
     */
    private String calculerEvolution(Long medecinId, YearMonth ym,
                                     Map<Long, String> statutsM,
                                     Map<Long, String> statutsM1,
                                     boolean apresCalculable) {
        if (!apresCalculable) return "NON_OBSERVABLE";

        String sAvant = statutsM.getOrDefault(medecinId, "exclu");
        String sApres = statutsM1.getOrDefault(medecinId, "exclu");

        int rangAvant = RANG_STATUT.getOrDefault(sAvant, 8);
        int rangApres = RANG_STATUT.getOrDefault(sApres, 8);

        if (rangApres < rangAvant) return "FAVORABLE";
        if (rangApres == rangAvant) return "STABLE";
        return "DEFAVORABLE";
    }

    /**
     * Détermine si le mois M+1 est calculable pour le mois de référence fourni.
     * M+1 est calculable si au moins une extraction existe sur cette période.
     * Méthode séparée de calculerEvolution pour pouvoir précalculer en batch.
     */
    public boolean isApresCalculable(YearMonth ym) {
        // On interroge directement la base : si des données existent pour M+1 → calculable
        YearMonth ymNext = ym.plusMonths(1);
        List<LocalDate> dates = extractionDonneesRepository.findAllDatesDescending();
        return dates.stream()
                .anyMatch(d -> YearMonth.from(d).equals(ymNext));
    }

    private boolean isVactis(RetourTerrain r) {
        return r.getAction() != null;
    }

    private boolean isRealisee(RetourTerrain r) {
        return r.getStatutVisite() != null && r.getStatutVisite() == StatutVisite.REALISEE;
    }

    private boolean isNonRealisee(RetourTerrain r) {
        return r.getStatutVisite() != null && r.getStatutVisite() == StatutVisite.NON_REALISEE;
    }

    private QualificationVisite qualif(RetourTerrain r) {
        return r.getQualification() != null ? r.getQualification() : QualificationVisite.NON_RENSEIGNE;
    }

    private TypeVisite typeVisiteSafe(RetourTerrain r) {
        return r.getTypeVisite() != null ? r.getTypeVisite() : TypeVisite.AUTRE;
    }

    private String commercialSafe(RetourTerrain r) {
        return (r.getVisiteur() != null && !r.getVisiteur().isBlank()) ? r.getVisiteur() : "Non renseigné";
    }

    private String buildNom(Medecin m) {
        if (m == null) return "—";
        String nom    = m.getNom()    != null ? m.getNom()    : "";
        String prenom = m.getPrenom() != null ? m.getPrenom() : "";
        return (nom + " " + prenom).trim();
    }

    private YearMonth parseOrDefault(String moisParam) {
        if (moisParam != null && !moisParam.isBlank()) {
            try {
                return YearMonth.parse(moisParam.trim(), YYYY_MM);
            } catch (Exception e) {
                log.warn("Niveau 4 — format de mois invalide : {}, fallback.", moisParam);
            }
        }
        List<LocalDate> dates = extractionDonneesRepository.findAllDatesDescending();
        return dates.isEmpty() ? YearMonth.now() : YearMonth.from(dates.get(0));
    }
}
