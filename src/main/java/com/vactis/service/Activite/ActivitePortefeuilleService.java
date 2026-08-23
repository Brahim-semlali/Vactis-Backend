package com.vactis.service.Activite;

import com.vactis.dto.activite.*;
import com.vactis.model.medecin.Medecin;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

// Service Niveau 2 — dynamique du portefeuille médecins (statuts VACTIS, transitions, flux, top mouvements)
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivitePortefeuilleService {

    private final ExtractionDonneesRepository extractionDonneesRepository;
    private final MedecinRepository medecinRepository;

    private static final DateTimeFormatter YYYY_MM = DateTimeFormatter.ofPattern("yyyy-MM");

    // Rang hiérarchique des 8 statuts VACTIS (rang 1 = meilleur état)
    private static final Map<String, Integer> RANG_STATUT = Map.of(
            "progression",       1,
            "actif_stable",      2,
            "surveillance",      3,
            "retention",         4,
            "silence_critique",  5,
            "onboarding",        6,
            "a_reactiver",       7,
            "exclu",             8
    );

    // Libellés métier affichés dans l'UI pour chaque statut
    private static final Map<String, String> LIBELLE_STATUT = Map.of(
            "progression",       "Trajectoire favorable.",
            "actif_stable",      "Activité stable.",
            "surveillance",      "Signal à suivre.",
            "retention",         "Risque commercial.",
            "silence_critique",  "Signal radio critique.",
            "onboarding",        "Nouveau potentiel.",
            "a_reactiver",       "Reprise à qualifier.",
            "exclu",             "Hors cycle actif."
    );

    // Couleur CSS associée à chaque statut (utilisée par le frontend)
    private static final Map<String, String> COULEUR_STATUT = Map.of(
            "progression",       "green",
            "actif_stable",      "green",
            "surveillance",      "orange",
            "retention",         "red",
            "silence_critique",  "red",
            "onboarding",        "blue",
            "a_reactiver",       "orange",
            "exclu",             "gray"
    );

    // Ordre d'affichage dans la grille 4×2 (conforme aux captures de référence)
    private static final List<String> ORDRE_AFFICHAGE = List.of(
            "progression", "actif_stable", "surveillance", "retention",
            "silence_critique", "onboarding", "a_reactiver", "exclu"
    );

    // Calcule la répartition des 8 statuts VACTIS pour tous les médecins sur le mois M (avec liste des médecins par statut)
    public StatutRepartitionResponse getRepartitionStatuts(String moisParam) {
        YearMonth ym = parseOrGetDefaultMois(moisParam);
        List<Medecin> medecins = medecinRepository.findAll();

        Map<Long, String> statutMap = buildStatutMapForMonth(ym, medecins);
        Map<String, Long> caM   = buildCaMap(ym);
        Map<String, Long> casM  = buildCasMap(ym);

        Map<String, List<MedecinStatutItem>> medecinsByStatut = new HashMap<>();
        for (String s : ORDRE_AFFICHAGE) medecinsByStatut.put(s, new ArrayList<>());

        for (Medecin m : medecins) {
            String statut = statutMap.getOrDefault(m.getId(), "exclu");
            MedecinStatutItem item = buildMedecinStatutItem(m, caM, casM);
            medecinsByStatut.computeIfAbsent(statut, k -> new ArrayList<>()).add(item);
        }

        List<StatutRepartitionResponse.StatutCount> statuts = ORDRE_AFFICHAGE.stream()
                .map(s -> {
                    List<MedecinStatutItem> list = medecinsByStatut.getOrDefault(s, List.of());
                    return StatutRepartitionResponse.StatutCount.builder()
                            .statut(s)
                            .libelle(LIBELLE_STATUT.get(s))
                            .couleur(COULEUR_STATUT.get(s))
                            .count(list.size())
                            .medecins(list)
                            .build();
                })
                .collect(Collectors.toList());

        return StatutRepartitionResponse.builder()
                .mois(ym.format(YYYY_MM))
                .statuts(statuts)
                .build();
    }

    // Compare les statuts M-1 → M et retourne les 5 compteurs agrégés de transitions
    public TransitionsStatutsResponse getTransitionsStatuts(String moisParam) {
        YearMonth ymM   = parseOrGetDefaultMois(moisParam);
        YearMonth ymMm1 = ymM.minusMonths(1);

        List<Medecin> medecins = medecinRepository.findAll();

        Map<Long, String> statutsM   = buildStatutMapForMonth(ymM, medecins);
        Map<Long, String> statutsMm1 = buildStatutMapForMonth(ymMm1, medecins);

        Map<String, Long> caM   = buildCaMap(ymM);
        Map<String, Long> caMm1 = buildCaMap(ymMm1);

        long totalEtudies = 0, favorables = 0, stables = 0, defavorables = 0, nouveauxMedecins = 0;

        for (Medecin m : medecins) {
            String statutM   = statutsM.getOrDefault(m.getId(), "exclu");
            String statutMm1 = statutsMm1.getOrDefault(m.getId(), "exclu");
            String key       = buildMedKey(m);
            boolean actifM   = caM.getOrDefault(key, 0L) > 0;
            boolean actifMm1 = caMm1.getOrDefault(key, 0L) > 0;

            if ("onboarding".equals(statutM) && actifM && !actifMm1) {
                nouveauxMedecins++;
                continue;
            }

            totalEtudies++;
            int rangM   = RANG_STATUT.getOrDefault(statutM, 8);
            int rangMm1 = RANG_STATUT.getOrDefault(statutMm1, 8);

            if (rangM < rangMm1)      favorables++;
            else if (rangM == rangMm1) stables++;
            else                       defavorables++;
        }

        return TransitionsStatutsResponse.builder()
                .moisPrecedent(ymMm1.format(YYYY_MM))
                .moisCourant(ymM.format(YYYY_MM))
                .totalEtudies(totalEtudies)
                .favorables(favorables)
                .stables(stables)
                .defavorables(defavorables)
                .nouveauxMedecins(nouveauxMedecins)
                .build();
    }

    // Liste toutes les paires (statut M-1 → statut M) avec leur effectif et liste des médecins concernés
    public FluxAgregesResponse getFluxAgreges(String moisParam) {
        YearMonth ymM   = parseOrGetDefaultMois(moisParam);
        YearMonth ymMm1 = ymM.minusMonths(1);

        List<Medecin> medecins = medecinRepository.findAll();

        Map<Long, String> statutsM   = buildStatutMapForMonth(ymM, medecins);
        Map<Long, String> statutsMm1 = buildStatutMapForMonth(ymMm1, medecins);
        Map<String, Long> caM        = buildCaMap(ymM);
        Map<String, Long> casM       = buildCasMap(ymM);

        Map<String, List<MedecinStatutItem>> fluxMedecinsMap = new LinkedHashMap<>();
        for (Medecin m : medecins) {
            String statM   = statutsM.getOrDefault(m.getId(), "exclu");
            String statMm1 = statutsMm1.getOrDefault(m.getId(), "exclu");
            String cle     = statMm1 + "|" + statM;
            MedecinStatutItem item = buildMedecinStatutItem(m, caM, casM);
            fluxMedecinsMap.computeIfAbsent(cle, k -> new ArrayList<>()).add(item);
        }

        List<FluxAgregeItem> flux = fluxMedecinsMap.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, List<MedecinStatutItem>> e) -> e.getValue().size()).reversed())
                .map(entry -> {
                    String[] parts  = entry.getKey().split("\\|", 2);
                    String statMm1  = parts[0];
                    String statM    = parts[1];
                    List<MedecinStatutItem> list = entry.getValue();

                    int rangM   = RANG_STATUT.getOrDefault(statM, 8);
                    int rangMm1 = RANG_STATUT.getOrDefault(statMm1, 8);

                    String typeTransition;
                    String couleurFlux;

                    if ("onboarding".equals(statM) && ("exclu".equals(statMm1) || statMm1 == null)) {
                        typeTransition = "onboarding";
                        couleurFlux = "blue";
                    } else if (rangM > rangMm1) {
                        // Passage d'un statut fort à un statut moins fort -> Défavorable (Rouge)
                        typeTransition = "defavorable";
                        couleurFlux = "red";
                    } else if (rangM < rangMm1) {
                        // Passage d'un statut moins fort à un statut plus fort -> Favorable (Vert)
                        typeTransition = "favorable";
                        couleurFlux = "green";
                    } else {
                        // Statut inchangé -> Stable (Gris)
                        typeTransition = "stable";
                        couleurFlux = "gray";
                    }

                    return FluxAgregeItem.builder()
                            .statutPrecedent(statMm1)
                            .statutCourant(statM)
                            .couleurPrecedent(COULEUR_STATUT.getOrDefault(statMm1, "gray"))
                            .couleurCourant(COULEUR_STATUT.getOrDefault(statM, "gray"))
                            .typeTransition(typeTransition)
                            .couleurFlux(couleurFlux)
                            .nombreMedecins(list.size())
                            .medecins(list)
                            .build();
                })
                .collect(Collectors.toList());

        return FluxAgregesResponse.builder()
                .mois(ymM.format(YYYY_MM))
                .totalFlux(flux.size())
                .flux(flux)
                .build();
    }

    // Calcule le delta CA ou cas entre M-1 et M par médecin, retourne top progressions et top baisses
    public TopMouvementsResponse getTopMouvements(String moisParam, String metrique, int limite) {
        YearMonth ymM   = parseOrGetDefaultMois(moisParam);
        YearMonth ymMm1 = ymM.minusMonths(1);

        boolean isCa = !"cas".equalsIgnoreCase(metrique);
        String unite = isCa ? "MAD" : "cas";

        Map<String, Long> mapM   = isCa ? buildCaMap(ymM)   : buildCasMap(ymM);
        Map<String, Long> mapMm1 = isCa ? buildCaMap(ymMm1) : buildCasMap(ymMm1);

        // Exclure les pseudo-médecins (dossiers non affectés importés avec un libellé de repli)
        List<Medecin> medecins = medecinRepository.findAll().stream()
                .filter(m -> !isPseudoMedecin(m))
                .collect(Collectors.toList());

        record MedecinDelta(Medecin medecin, long valM, long valMm1, long delta) {}

        List<MedecinDelta> deltas = new ArrayList<>();
        for (Medecin m : medecins) {
            String key  = buildMedKey(m);
            long valM   = mapM.getOrDefault(key, 0L);
            long valMm1 = mapMm1.getOrDefault(key, 0L);
            if (valM == 0 && valMm1 == 0) continue;
            deltas.add(new MedecinDelta(m, valM, valMm1, valM - valMm1));
        }

        List<TopMouvementItem> progressions = deltas.stream()
                .filter(d -> d.delta() > 0)
                .sorted(Comparator.comparingLong(MedecinDelta::delta).reversed())
                .limit(limite)
                .map(d -> buildTopItem(d.medecin(), d.valM(), d.valMm1(), d.delta(), unite))
                .collect(Collectors.toList());

        List<TopMouvementItem> baisses = deltas.stream()
                .filter(d -> d.delta() < 0)
                .sorted(Comparator.comparingLong(MedecinDelta::delta))
                .limit(limite)
                .map(d -> buildTopItem(d.medecin(), d.valM(), d.valMm1(), d.delta(), unite))
                .collect(Collectors.toList());

        return TopMouvementsResponse.builder()
                .mois(ymM.format(YYYY_MM))
                .moisPrecedent(ymMm1.format(YYYY_MM))
                .metrique(isCa ? "ca" : "cas")
                .limite(limite)
                .progressions(progressions)
                .baisses(baisses)
                .build();
    }

    /**
     * Détermine si un médecin est un pseudo-médecin (libellé de repli non nominatif importé depuis l'Excel).
     * Ces entrées correspondent à des dossiers dont la colonne "médecin" dans l'Excel contenait
     * une valeur non nominative (ex. "INCONNU MÉDECIN", "PRÉCISÉ NON", nom vide).
     * Règle : on exclut les médecins dont le nom (après nettoyage) est vide, null, ou dans la liste
     * des libellés de repli connus.
     */
    private static final Set<String> NOMS_EXCLUS_TOP = Set.of(
            "inconnu", "inconnu medecin", "inconnu médecin",
            "précisé non", "precise non", "non précisé", "non precise",
            "nr", "n/r", "n.r.", "–", "-", "/"
    );

    private boolean isPseudoMedecin(Medecin m) {
        String nom    = m.getNom()    != null ? m.getNom().trim()    : "";
        String prenom = m.getPrenom() != null ? m.getPrenom().trim() : "";
        String full   = (prenom + " " + nom).trim().toLowerCase();
        if (full.isBlank()) return true;
        return NOMS_EXCLUS_TOP.stream().anyMatch(exclu -> full.contains(exclu));
    }


    // Calcule le statut VACTIS d'un médecin à partir de la variation CA M / CA M-1
    private String calculerStatutComplet(
            Medecin m,
            Map<String, Long> caM,
            Map<String, Long> caMm1,
            Set<Long> actifHisto,
            Set<Long> onboardingIds
    ) {
        String key  = buildMedKey(m);
        long caCurr = caM.getOrDefault(key, 0L);
        long caPrev = caMm1.getOrDefault(key, 0L);

        if (onboardingIds.contains(m.getId()) && caCurr > 0) return "onboarding";
        if (caCurr == 0) return actifHisto.contains(m.getId()) ? "a_reactiver" : "exclu";
        if (caPrev == 0) return "actif_stable";

        double ratio = (double) caCurr / (double) caPrev;
        if (ratio < 0.30) return "silence_critique";
        if (ratio < 0.60) return "retention";     // variation < -40%
        if (ratio < 0.90) return "surveillance";   // variation entre -40% et -10% (ratio < 0.90)
        if (ratio > 1.20) return "progression";    // variation > +20% (ratio > 1.20)
        return "actif_stable";                     // variation entre -10% et +20% (0.90 <= ratio <= 1.20)
    }

    // Construit un MedecinStatutItem à partir d'un médecin et de ses valeurs M
    private MedecinStatutItem buildMedecinStatutItem(Medecin m, Map<String, Long> caM, Map<String, Long> casM) {
        String key = buildMedKey(m);
        String nom = ((m.getNom() != null ? m.getNom() : "") + " "
                + (m.getPrenom() != null ? m.getPrenom() : "")).trim().toUpperCase();
        return MedecinStatutItem.builder()
                .id(m.getId())
                .codeMedecin(m.getCodeMedecin())
                .nom(nom)
                .specialite(m.getSpecialite())
                .caM(caM.getOrDefault(key, 0L))
                .casM(casM.getOrDefault(key, 0L))
                .build();
    }

    // Construit la map {medecinId → statut VACTIS} pour tous les médecins sur le mois ym
    // Méthode publique : réutilisée par ActiviteImpactService (Niveau 4) pour calculer statut avant/après
    public Map<Long, String> buildStatutMapForMonth(YearMonth ym, List<Medecin> medecins) {
        Map<String, Long> caM   = buildCaMap(ym);
        Map<String, Long> caMm1 = buildCaMap(ym.minusMonths(1));
        Set<Long> actifHisto    = buildHistoriqueActifIds(ym);
        Set<Long> onboarding    = buildOnboardingIds(ym, medecins);

        Map<Long, String> result = new LinkedHashMap<>();
        for (Medecin m : medecins) {
            result.put(m.getId(), calculerStatutComplet(m, caM, caMm1, actifHisto, onboarding));
        }
        return result;
    }

    // Construit la map {idMedecin → CA total} pour un mois donné
    private Map<String, Long> buildCaMap(YearMonth ym) {
        List<Object[]> rows = extractionDonneesRepository
                .sumCaByMedecinAndDateRange(ym.atDay(1), ym.atEndOfMonth());
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : rows) map.put(String.valueOf((Long) row[0]), ((Number) row[1]).longValue());
        return map;
    }

    // Construit la map {idMedecin → nombre de cas} pour un mois donné
    private Map<String, Long> buildCasMap(YearMonth ym) {
        List<Object[]> rows = extractionDonneesRepository
                .countCasByMedecinAndDateRange(ym.atDay(1), ym.atEndOfMonth());
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : rows) map.put(String.valueOf((Long) row[0]), ((Number) row[1]).longValue());
        return map;
    }

    // Retourne les IDs des médecins ayant eu au moins un dossier dans les 6 mois précédant ym
    private Set<Long> buildHistoriqueActifIds(YearMonth ym) {
        LocalDate end   = ym.atDay(1).minusDays(1);
        LocalDate start = ym.minusMonths(6).atDay(1);
        return new HashSet<>(extractionDonneesRepository.findMedecinIdsWithActivityInRange(start, end));
    }

    // Retourne les IDs des médecins en onboarding : datePremiereCollaboration dans ym ou ym-1
    private Set<Long> buildOnboardingIds(YearMonth ym, List<Medecin> medecins) {
        LocalDate debut = ym.minusMonths(1).atDay(1);
        LocalDate fin   = ym.atEndOfMonth();
        return medecins.stream()
                .filter(m -> m.getDatePremiereCollaboration() != null
                        && !m.getDatePremiereCollaboration().isBefore(debut)
                        && !m.getDatePremiereCollaboration().isAfter(fin))
                .map(Medecin::getId)
                .collect(Collectors.toSet());
    }

    // Clé de lookup dans les maps CA/cas (ID du médecin en String)
    private String buildMedKey(Medecin m) {
        return String.valueOf(m.getId());
    }

    // Construit un TopMouvementItem à partir d'un médecin et de ses valeurs M/M-1
    private TopMouvementItem buildTopItem(Medecin m, long valM, long valMm1, long delta, String unite) {
        String nom = ((m.getNom() != null ? m.getNom() : "") + " "
                + (m.getPrenom() != null ? m.getPrenom() : "")).trim().toUpperCase();
        return TopMouvementItem.builder()
                .nomMedecin(nom)
                .specialite(m.getSpecialite())
                .valeurM(valM)
                .valeurMm1(valMm1)
                .delta(delta)
                .unite(unite)
                .build();
    }

    // Résout le mois depuis le paramètre ou retourne le mois le plus récent disponible en base
    private YearMonth parseOrGetDefaultMois(String moisParam) {
        if (moisParam != null && !moisParam.isBlank()) {
            try {
                return YearMonth.parse(moisParam.trim(), YYYY_MM);
            } catch (Exception e) {
                log.warn("Format de mois invalide: {}, fallback au mois disponible.", moisParam);
            }
        }
        List<LocalDate> dates = extractionDonneesRepository.findAllDatesDescending();
        return dates.isEmpty() ? YearMonth.now() : YearMonth.from(dates.get(0));
    }
}
