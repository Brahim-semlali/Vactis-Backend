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

    // Rang hiérarchique des 7 statuts VACTIS cibles (rang 1 = meilleur état)
    private static final Map<String, Integer> RANG_STATUT = Map.of(
            "progression",       1,
            "actif_stable",      2,
            "surveillance",      3,
            "retention",         4,
            "onboarding",        5,
            "a_reactiver",       6,
            "inactif",           7,
            // Fallbacks rétrocompatibles
            "silence_critique",  4,
            "exclu",             7
    );

    // Libellés métier affichés dans l'UI pour chaque statut
    private static final Map<String, String> LIBELLE_STATUT = Map.of(
            "progression",       "Trajectoire favorable.",
            "actif_stable",      "Activité stable.",
            "surveillance",      "Signal à suivre.",
            "retention",         "Risque commercial.",
            "onboarding",        "Nouveau potentiel.",
            "a_reactiver",       "Reprise à qualifier (manuel).",
            "inactif",           "Hors cycle actif (> 60 jours).",
            // Fallbacks rétrocompatibles
            "silence_critique",  "Signal radio critique.",
            "exclu",             "Hors cycle actif."
    );

    // Couleur CSS associée à chaque statut (utilisée par le frontend)
    private static final Map<String, String> COULEUR_STATUT = Map.of(
            "progression",       "green",
            "actif_stable",      "green",
            "surveillance",      "orange",
            "retention",         "red",
            "onboarding",        "blue",
            "a_reactiver",       "orange",
            "inactif",           "gray",
            // Fallbacks rétrocompatibles
            "silence_critique",  "red",
            "exclu",             "gray"
    );

    // Ordre d'affichage dans la grille (7 statuts officiels)
    private static final List<String> ORDRE_AFFICHAGE = List.of(
            "progression", "actif_stable", "surveillance", "retention",
            "onboarding", "a_reactiver", "inactif"
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


    // Calcule le statut VACTIS selon les règles cibles (7 statuts, variation mixte 60/40, seuil 60j inactif, réactivation manuelle et neutralisation irrégulière)
    private String calculerStatutComplet(
            Medecin m,
            Map<String, Long> caM,
            Map<String, Long> caMm1,
            Map<String, Long> caMm2,
            Map<String, Long> caMm3,
            Map<String, Long> casM,
            Map<String, Long> casMm1,
            Map<String, Long> casMm2,
            Map<String, Long> casMm3,
            Set<Long> actif60Jours,
            Set<Long> onboardingIds
    ) {
        String key  = buildMedKey(m);
        long caCurr = caM.getOrDefault(key, 0L);

        // 1. Statut forcé manuellement "À réactiver"
        if (Boolean.TRUE.equals(m.getIsAReactiverManuel()) && caCurr == 0) {
            return "a_reactiver";
        }

        // 2. Onboarding (nouveau médecin avec 1ère activité constatée)
        if (onboardingIds.contains(m.getId()) && caCurr > 0) {
            return "onboarding";
        }

        // 3. Aucun CA sur le mois M : seuil de 60 jours sans aucun dossier -> Inactif
        if (caCurr == 0) {
            return actif60Jours.contains(m.getId()) ? "retention" : "inactif";
        }

        // 4. Calcul de la Variation Mixte (60% CA + 40% volume de cas)
        double caReference = average(caMm1, caMm2, caMm3, key);
        double casReference = average(casMm1, casMm2, casMm3, key);
        double variationCa = ((caCurr - caReference) / Math.max(caReference, 300.0)) * 100.0;
        double variationCas = ((casM.getOrDefault(key, 0L) - casReference) / Math.max(casReference, 1.0)) * 100.0;
        double variationMixte = (0.60 * variationCa) + (0.40 * variationCas);

        String statutTheorique;
        if (variationMixte > 20.0) {
            statutTheorique = "progression";
        } else if (variationMixte >= -10.0) {
            statutTheorique = "actif_stable";
        } else if (variationMixte >= -40.0) {
            statutTheorique = "surveillance";
        } else {
            // Dans le nouveau référentiel à 7 statuts, toute chute sous -40% est en Rétention (le silence critique est un score transversal)
            statutTheorique = "retention";
        }

        // 5. Section 4 — Activité Irrégulière (8 conditions cumulatives)
        // Neutralise un faux signal de Surveillance ou Rétention si le prescripteur a un profil irrégulier historique
        if (("surveillance".equals(statutTheorique) || "retention".equals(statutTheorique))
                && verifier8ConditionsActiviteIrreguliere(m, caCurr, caMm1.getOrDefault(key, 0L), caMm2.getOrDefault(key, 0L), caMm3.getOrDefault(key, 0L), casM.getOrDefault(key, 0L), casReference)) {
            m.setIsProfilIrregulier(true);
            return "actif_stable";
        } else {
            m.setIsProfilIrregulier(false);
        }

        return statutTheorique;
    }

    /**
     * Vérifie les 8 conditions cumulatives définissant un prescripteur à profil d'activité irrégulière (Section 4) :
     * 1. Ancienneté de collaboration >= 6 mois
     * 2. Activité constatée sur au moins 2 des 3 derniers mois de référence (M-1, M-2, M-3)
     * 3. Variabilité historique constatée (écart entre mois min et mois max >= 30%)
     * 4. Volume global historique significatif (au moins 5 cas au total)
     * 5. Activité non nulle sur le mois courant (caCurr > 0)
     * 6. Panier moyen préservé (ratio CA/cas >= 65% de la moyenne de référence)
     * 7. Aucune réclamation active non résolue
     * 8. Prescripteur actif du portefeuille
     */
    public boolean verifier8ConditionsActiviteIrreguliere(
            Medecin m,
            long caCurr,
            long caMm1,
            long caMm2,
            long caMm3,
            long casCurr,
            double casRef
    ) {
        if (m == null) return false;

        // Condition 1 : Ancienneté >= 6 mois
        boolean ancienneteOk = m.getDatePremiereCollaboration() != null
                && !m.getDatePremiereCollaboration().plusMonths(6).isAfter(LocalDate.now());
        if (!ancienneteOk && m.getCreatedAt() != null) {
            ancienneteOk = !m.getCreatedAt().toLocalDate().plusMonths(6).isAfter(LocalDate.now());
        }
        if (!ancienneteOk) return false;

        // Condition 2 : Au moins 2 mois actifs parmi M-1, M-2, M-3
        int moisActifsRef = 0;
        if (caMm1 > 0) moisActifsRef++;
        if (caMm2 > 0) moisActifsRef++;
        if (caMm3 > 0) moisActifsRef++;
        if (moisActifsRef < 2) return false;

        // Condition 3 : Variabilité historique (écart relatif max - min >= 30%)
        long maxHist = Math.max(caMm1, Math.max(caMm2, caMm3));
        long minHist = Math.min(caMm1, Math.min(caMm2, caMm3));
        if (maxHist > 0 && ((double)(maxHist - minHist) / maxHist) < 0.30) {
            return false;
        }

        // Condition 4 : Volume total de cas >= 5
        if (m.getTotalCas() != null && m.getTotalCas() < 5) return false;

        // Condition 5 : Activité non nulle sur le mois courant
        if (caCurr <= 0) return false;

        // Condition 6 : Panier moyen préservé (CA / cas)
        if (casCurr > 0 && casRef > 0) {
            double panierCurr = (double) caCurr / casCurr;
            double caRefMoy = (caMm1 + caMm2 + caMm3) / 3.0;
            double panierRef = caRefMoy / casRef;
            if (panierRef > 0 && (panierCurr / panierRef) < 0.65) {
                return false;
            }
        }

        // Condition 7 : Absence de réclamation bloquante
        if (m.getCommentaire() != null && m.getCommentaire().toLowerCase().contains("reclamation")) {
            return false;
        }

        // Condition 8 : Prescripteur non inactif
        return m.getStatut() == null || !"INACTIF".equalsIgnoreCase(m.getStatut());
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
        if (medecins.isEmpty()) return new LinkedHashMap<>();
        Map<String, Long> caM   = buildCaMap(ym);
        Map<String, Long> caMm1 = buildCaMap(ym.minusMonths(1));
        Map<String, Long> caMm2 = buildCaMap(ym.minusMonths(2));
        Map<String, Long> caMm3 = buildCaMap(ym.minusMonths(3));
        Map<String, Long> casM = buildCasMap(ym);
        Map<String, Long> casMm1 = buildCasMap(ym.minusMonths(1));
        Map<String, Long> casMm2 = buildCasMap(ym.minusMonths(2));
        Map<String, Long> casMm3 = buildCasMap(ym.minusMonths(3));
        Set<Long> actif60Jours  = buildHistoriqueActif60Jours(ym);
        Set<Long> onboarding    = buildOnboardingIds(ym, medecins);

        Map<Long, String> result = new LinkedHashMap<>();
        for (Medecin m : medecins) {
            result.put(m.getId(), calculerStatutComplet(m, caM, caMm1, caMm2, caMm3, casM, casMm1, casMm2, casMm3, actif60Jours, onboarding));
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

    private double average(Map<String, Long> first, Map<String, Long> second, Map<String, Long> third, String key) {
        return (first.getOrDefault(key, 0L) + second.getOrDefault(key, 0L) + third.getOrDefault(key, 0L)) / 3.0;
    }

    // Construit la map {idMedecin → nombre de cas} pour un mois donné
    private Map<String, Long> buildCasMap(YearMonth ym) {
        List<Object[]> rows = extractionDonneesRepository
                .countCasByMedecinAndDateRange(ym.atDay(1), ym.atEndOfMonth());
        Map<String, Long> map = new HashMap<>();
        if (rows == null) return map;
        for (Object[] row : rows) map.put(String.valueOf((Long) row[0]), ((Number) row[1]).longValue());
        return map;
    }

    // Retourne les IDs des médecins ayant eu au moins un dossier dans les 60 jours précédant la fin du mois ym
    private Set<Long> buildHistoriqueActif60Jours(YearMonth ym) {
        LocalDate end   = ym.atEndOfMonth();
        LocalDate start = end.minusDays(60);
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
