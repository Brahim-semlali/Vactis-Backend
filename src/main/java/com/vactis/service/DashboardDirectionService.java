package com.vactis.service;

import com.vactis.dto.activite.RapportImpactResponse;
import com.vactis.dto.bridgetogoal.BridgeToGoalResponse;
import com.vactis.dto.direction.DashboardDirectionResponse;
import com.vactis.dto.direction.DashboardDirectionResponse.CelluleSituation;
import com.vactis.dto.direction.DashboardDirectionResponse.Execution;
import com.vactis.dto.direction.DashboardDirectionResponse.LigneSegment;
import com.vactis.dto.direction.DashboardDirectionResponse.MedecinPrioritaire;
import com.vactis.dto.direction.DashboardDirectionResponse.PointMensuel;
import com.vactis.dto.direction.DashboardDirectionResponse.Portefeuille;
import com.vactis.dto.direction.DashboardDirectionResponse.SujetArbitrage;
import com.vactis.dto.direction.DashboardDirectionResponse.Trajectoire;
import com.vactis.model.action.Action;
import com.vactis.model.action.EtatAction;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.reclamation.PrioriteReclamation;
import com.vactis.model.reclamation.Reclamation;
import com.vactis.model.reclamation.StatutReclamation;
import com.vactis.repository.ActionRepository;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.ReclamationRepository;
import com.vactis.service.Activite.ActiviteImpactService;
import com.vactis.service.Activite.ActivitePortefeuilleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Assemble la synthèse Direction à partir des calculs existants (Bridge to Goal,
 * rapport d'impact terrain, indicateurs médecins) sans les dupliquer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardDirectionService {

    static final String SITUATION_RETENTION = "RETENTION";
    static final String SITUATION_SILENCE = "SILENCE";
    static final String SITUATION_SURVEILLANCE = "SURVEILLANCE";

    static final Set<StatutReclamation> STATUTS_FERMES = EnumSet.of(
            StatutReclamation.TRAITEE, StatutReclamation.CLOTURE_SANS_ACTION,
            StatutReclamation.RESOLUE, StatutReclamation.REJETEE, StatutReclamation.CLOTUREE);

    private static final int NB_MOIS_TENDANCE = 6;
    private static final int NB_MOIS_REFERENCE = 3;
    private static final int NB_PRIORITAIRES = 10;
    private static final Map<String, Integer> ORDRE_SITUATION = Map.of(
            SITUATION_RETENTION, 0, SITUATION_SILENCE, 1, SITUATION_SURVEILLANCE, 2);

    private final ExtractionDonneesRepository extractionDonneesRepository;
    private final MedecinRepository medecinRepository;
    private final ActionRepository actionRepository;
    private final ReclamationRepository reclamationRepository;
    private final ActiviteImpactService activiteImpactService;
    private final BridgeToGoalService bridgeToGoalService;
    private final ActivitePortefeuilleService portefeuilleService;

    @Transactional(readOnly = true)
    public DashboardDirectionResponse getDashboard(String moisParam) {
        YearMonth ym = resoudreMois(moisParam);
        String mois = ym.toString();

        Trajectoire trajectoire = construireTrajectoire(ym);
        Portefeuille portefeuille = construirePortefeuille(ym);
        Execution execution = construireExecution(mois);

        return DashboardDirectionResponse.builder()
                .mois(mois)
                .trajectoire(trajectoire)
                .portefeuille(portefeuille)
                .execution(execution)
                .sujets(construireSujets(trajectoire, portefeuille, execution))
                .build();
    }

    // ── Période ──────────────────────────────────────────────────────────────

    YearMonth resoudreMois(String moisParam) {
        if (moisParam != null && !moisParam.isBlank()) {
            try {
                return YearMonth.parse(moisParam.trim());
            } catch (Exception e) {
                log.warn("Dashboard Direction : mois invalide '{}', utilisation du dernier mois disponible.", moisParam);
            }
        }
        List<LocalDate> dates = extractionDonneesRepository.findAllDatesDescending();
        return dates == null || dates.isEmpty() ? YearMonth.now() : YearMonth.from(dates.get(0));
    }

    // ── A. Résultat et trajectoire ───────────────────────────────────────────

    private Trajectoire construireTrajectoire(YearMonth ym) {
        long caMois = sommeCa(ym.atDay(1), ym.atEndOfMonth());
        long casMois = nz(extractionDonneesRepository.countCasByDateRange(ym.atDay(1), ym.atEndOfMonth()));
        long caNonAffecte = nz(extractionDonneesRepository.sumPrixAPayerNonAffectesByDateRange(ym.atDay(1), ym.atEndOfMonth()));
        YearMonth ymN1 = ym.minusYears(1);
        long caMoisN1 = sommeCa(ymN1.atDay(1), ymN1.atEndOfMonth());

        LocalDate debutAnnee = LocalDate.of(ym.getYear(), 1, 1);
        long caCumule = sommeCa(debutAnnee, ym.atEndOfMonth());
        long caCumuleN1 = sommeCa(debutAnnee.minusYears(1), ym.atEndOfMonth().minusYears(1));
        long projection = BridgeToGoalPolicy.runRateAnnuel(caCumule, ym.getMonthValue());

        BridgeToGoalResponse bridge = chargerObjectifs(ym);
        boolean objectifDefini = bridge != null;
        Long caPortefeuille = bridge != null && bridge.getCaPortefeuille() != null
                ? bridge.getCaPortefeuille()
                : nz(extractionDonneesRepository.sumPrixAPayerWithMedecinByDateRange(ym.atDay(1), ym.atEndOfMonth()));
        Long objectifMois = objectifDefini ? bridge.getMonthlyTargetBudget() : null;
        Long objectifCumule = objectifDefini ? bridge.getObjectifYtdProratise() : null;

        return Trajectoire.builder()
                .caMois(caMois)
                .casMois(casMois)
                .caPortefeuilleMois(caPortefeuille)
                .caNonAffecteMois(caNonAffecte)
                .objectifDefini(objectifDefini)
                .objectifMois(objectifMois)
                .ecartObjectifMois(objectifMois == null ? null : caMois - objectifMois)
                .tauxAtteinteMois(objectifMois == null || objectifMois <= 0 ? null : arrondi1(caMois * 100.0 / objectifMois))
                .caMoisNMoins1(caMoisN1)
                .evolutionMoisNMoins1Pct(evolutionPct(caMois, caMoisN1))
                .caCumule(caCumule)
                .objectifCumuleProratise(objectifCumule)
                .ecartObjectifCumule(objectifCumule == null ? null : caCumule - objectifCumule)
                .caCumuleNMoins1(caCumuleN1)
                .evolutionCumuleNMoins1Pct(evolutionPct(caCumule, caCumuleN1))
                .objectifAnnuel(objectifDefini ? bridge.getAnnualTargetBudget() : null)
                .projectionAnnuelle(projection)
                .statutTrajectoire(objectifDefini ? bridge.getStatutYtd() : "OBJECTIF_NON_DEFINI")
                .tendance(construireTendance(ym))
                .build();
    }

    private BridgeToGoalResponse chargerObjectifs(YearMonth ym) {
        try {
            return bridgeToGoalService.simulerTrajectoire(ym.toString());
        } catch (ResponseStatusException e) {
            // Aucun objectif paramétré : la synthèse reste disponible sans les écarts.
            log.info("Dashboard Direction : objectif non disponible ({}).", e.getReason());
            return null;
        }
    }

    private List<PointMensuel> construireTendance(YearMonth fin) {
        List<PointMensuel> points = new ArrayList<>();
        for (int i = NB_MOIS_TENDANCE - 1; i >= 0; i--) {
            YearMonth m = fin.minusMonths(i);
            YearMonth mN1 = m.minusYears(1);
            points.add(PointMensuel.builder()
                    .mois(m.toString())
                    .ca(sommeCa(m.atDay(1), m.atEndOfMonth()))
                    .cas(nz(extractionDonneesRepository.countCasByDateRange(m.atDay(1), m.atEndOfMonth())))
                    .caNMoins1(sommeCa(mN1.atDay(1), mN1.atEndOfMonth()))
                    .build());
        }
        return points;
    }

    // ── B. Portefeuille à arbitrer ───────────────────────────────────────────

    /**
     * Portefeuille évalué pour le mois choisi (et non à la date du dernier import) :
     * - situation = statut mensuel VACTIS du mois (même calcul que Lecture activité) ;
     * - CA de référence = moyenne mensuelle des 3 mois précédant le mois choisi ;
     * - jours sans activité = depuis le dernier dossier jusqu'à la fin du mois choisi.
     */
    private Portefeuille construirePortefeuille(YearMonth ym) {
        List<Medecin> medecinsAB = medecinRepository.findAll().stream()
                .filter(m -> !medecinRepository.isPseudoMedecin(m))
                .filter(m -> List.of("A", "B").contains(segmentNormalise(m.getSegment())))
                .toList();
        Map<Long, String> statutsMois = portefeuilleService.buildStatutMapForMonth(ym, medecinsAB);
        Map<Long, Long> caReference = caMoyenParMedecin(ym.minusMonths(NB_MOIS_REFERENCE), ym.minusMonths(1));
        LocalDate finMois = ym.atEndOfMonth();

        List<MedecinPrioritaire> candidats = new ArrayList<>();
        for (Medecin m : medecinsAB) {
            String situation = situationMensuelle(statutsMois.get(m.getId()));
            if (situation == null) continue;
            candidats.add(MedecinPrioritaire.builder()
                    .id(m.getId())
                    .nom(nomComplet(m))
                    .specialite(m.getSpecialite())
                    .segment(segmentNormalise(m.getSegment()))
                    .situation(situation)
                    .caReference(caReference.getOrDefault(m.getId(), 0L))
                    .joursSansActivite(joursSansActivite(m.getId(), finMois))
                    .build());
        }

        List<LigneSegment> lignes = List.of(ligneSegment("A", candidats), ligneSegment("B", candidats));
        List<MedecinPrioritaire> prioritaires = candidats.stream()
                .sorted(Comparator
                        .comparing((MedecinPrioritaire p) -> ORDRE_SITUATION.get(p.getSituation()))
                        .thenComparing(MedecinPrioritaire::getSegment)
                        .thenComparing(MedecinPrioritaire::getCaReference, Comparator.reverseOrder()))
                .limit(NB_PRIORITAIRES)
                .toList();

        return Portefeuille.builder()
                .segments(lignes)
                .totalMedecins(candidats.size())
                .totalCaReference(candidats.stream().mapToLong(MedecinPrioritaire::getCaReference).sum())
                .prioritaires(prioritaires)
                .build();
    }

    /**
     * Traduit le statut mensuel VACTIS en situation à arbitrer :
     * rétention (baisse forte), silence (plus d'activité, à réactiver), surveillance (baisse modérée).
     */
    static String situationMensuelle(String statutMensuel) {
        if (statutMensuel == null) return null;
        return switch (statutMensuel.trim().toLowerCase(Locale.ROOT)) {
            case "retention", "silence_critique" -> SITUATION_RETENTION;
            case "a_reactiver" -> SITUATION_SILENCE;
            case "surveillance" -> SITUATION_SURVEILLANCE;
            default -> null;
        };
    }

    /** CA mensuel moyen par médecin entre deux mois inclus. */
    private Map<Long, Long> caMoyenParMedecin(YearMonth debut, YearMonth fin) {
        int nbMois = (int) java.time.temporal.ChronoUnit.MONTHS.between(debut, fin) + 1;
        Map<Long, Long> moyennes = new java.util.HashMap<>();
        for (Object[] ligne : extractionDonneesRepository.sumCaByMedecinAndDateRange(debut.atDay(1), fin.atEndOfMonth())) {
            moyennes.put(((Number) ligne[0]).longValue(), Math.round(((Number) ligne[1]).doubleValue() / nbMois));
        }
        return moyennes;
    }

    /** Jours écoulés entre le dernier dossier reçu (au plus tard à la date donnée) et cette date. */
    private Integer joursSansActivite(Long medecinId, LocalDate date) {
        return extractionDonneesRepository.findDatesReceptionByMedecinId(medecinId).stream()
                .filter(d -> !d.isAfter(date))
                .findFirst()
                .map(d -> (int) java.time.temporal.ChronoUnit.DAYS.between(d, date))
                .orElse(null);
    }

    static String segmentNormalise(String segment) {
        if (segment == null) return "";
        return segment.toUpperCase(Locale.ROOT).replace("SEGMENT", "").trim();
    }

    private LigneSegment ligneSegment(String segment, List<MedecinPrioritaire> candidats) {
        return LigneSegment.builder()
                .segment(segment)
                .retention(cellule(segment, SITUATION_RETENTION, candidats))
                .silence(cellule(segment, SITUATION_SILENCE, candidats))
                .surveillance(cellule(segment, SITUATION_SURVEILLANCE, candidats))
                .build();
    }

    private CelluleSituation cellule(String segment, String situation, List<MedecinPrioritaire> candidats) {
        List<MedecinPrioritaire> cible = candidats.stream()
                .filter(p -> segment.equals(p.getSegment()) && situation.equals(p.getSituation()))
                .toList();
        return CelluleSituation.builder()
                .nombre(cible.size())
                .caReference(cible.stream().mapToLong(MedecinPrioritaire::getCaReference).sum())
                .build();
    }

    // ── C. Exécution commerciale ─────────────────────────────────────────────

    private Execution construireExecution(String cycle) {
        RapportImpactResponse impact = activiteImpactService.getRapportImpact(cycle);
        List<Action> actionsCycle = actionRepository.findAll().stream()
                .filter(a -> cycle.equals(a.getCycleMensuel()))
                .toList();
        LocalDate aujourdhui = LocalDate.now();
        List<Reclamation> ouvertes = reclamationRepository.findAll().stream()
                .filter(r -> r.getStatut() == null || !STATUTS_FERMES.contains(r.getStatut()))
                .toList();

        return Execution.builder()
                .cycle(cycle)
                .actionsDuCycle(impact.getActionsVactisGenerees())
                .actionsPlanifiees(actionsCycle.stream().filter(a -> a.getEtatAction() == EtatAction.PLANIFIEE).count())
                .visitesVactisRealisees(impact.getVactisRealisees())
                .visitesVactisNonRealisees(impact.getVactisNonRealisees())
                .actionsSansRetour(impact.getSanRetourTerrain())
                .actionsEnRetard(actionsCycle.stream().filter(a -> Boolean.TRUE.equals(a.getEstEnRetard())).count())
                .tauxRealisation(impact.getTauxRealisation())
                .visitesLibresRealisees(impact.getVisitesHorsVactisRealisees())
                .reclamationsOuvertes((long) ouvertes.size())
                .reclamationsEcheanceDepassee(ouvertes.stream()
                        .filter(r -> r.getDateEcheance() != null && r.getDateEcheance().isBefore(aujourdhui)).count())
                .reclamationsSansEcheance(ouvertes.stream().filter(r -> r.getDateEcheance() == null).count())
                .build();
    }

    // ── D. Sujets à arbitrer ─────────────────────────────────────────────────

    List<SujetArbitrage> construireSujets(Trajectoire t, Portefeuille p, Execution e) {
        List<SujetArbitrage> sujets = new ArrayList<>();

        if (!Boolean.TRUE.equals(t.getObjectifDefini())) {
            sujets.add(sujet("ATTENTION", "Objectif non paramétré",
                    "Sans objectif annuel, l'écart à la cible ne peut pas être calculé.", "/bridge-to-goal"));
        } else if (t.getEcartObjectifCumule() != null && t.getEcartObjectifCumule() < 0) {
            long objectif = nz(t.getObjectifCumuleProratise());
            boolean important = objectif > 0 && t.getEcartObjectifCumule() * 100.0 / objectif <= -10.0;
            sujets.add(sujet(important ? "CRITIQUE" : "ATTENTION", "Retard sur l'objectif cumulé",
                    String.format(Locale.FRANCE, "%,d MAD sous l'objectif proratisé à fin %s.",
                            Math.abs(t.getEcartObjectifCumule()), e.getCycle()),
                    "/bridge-to-goal"));
        }

        p.getSegments().forEach(ligne -> {
            int critiques = ligne.getRetention().getNombre() + ligne.getSilence().getNombre();
            if (critiques > 0) {
                long ca = ligne.getRetention().getCaReference() + ligne.getSilence().getCaReference();
                sujets.add(sujet("A".equals(ligne.getSegment()) ? "CRITIQUE" : "ATTENTION",
                        critiques + " médecin(s) segment " + ligne.getSegment() + " en rétention ou silence",
                        String.format(Locale.FRANCE, "CA mensuel de référence concerné : %,d MAD (poids du portefeuille, pas une perte prévue).", ca),
                        "/medecins"));
            }
        });

        if (nz(e.getReclamationsEcheanceDepassee()) > 0) {
            sujets.add(sujet("CRITIQUE", e.getReclamationsEcheanceDepassee() + " réclamation(s) en retard",
                    "Échéance de traitement dépassée.", "/reclamations"));
        }
        long hautes = reclamationRepository.findAll().stream()
                .filter(r -> r.getStatut() == null || !STATUTS_FERMES.contains(r.getStatut()))
                .filter(r -> r.getPriorite() == PrioriteReclamation.HAUTE || r.getPriorite() == PrioriteReclamation.URGENTE)
                .count();
        if (hautes > 0) {
            sujets.add(sujet("ATTENTION", hautes + " réclamation(s) ouverte(s) de priorité haute ou urgente",
                    "À affecter à un responsable avec une échéance.", "/reclamations"));
        }
        if (nz(e.getActionsEnRetard()) > 0) {
            sujets.add(sujet("ATTENTION", e.getActionsEnRetard() + " action(s) en retard",
                    "Actions du cycle " + e.getCycle() + " dont l'échéance est dépassée.", "/actions"));
        }
        if (nz(e.getActionsSansRetour()) > 0) {
            sujets.add(sujet("ATTENTION", e.getActionsSansRetour() + " action(s) sans retour terrain",
                    "Actions générées sur le cycle sans visite renseignée.", "/actions"));
        }
        long caMois = nz(t.getCaMois());
        if (caMois > 0 && nz(t.getCaNonAffecteMois()) * 100.0 / caMois > 5.0) {
            sujets.add(sujet("ATTENTION", "Qualité des rattachements",
                    String.format(Locale.FRANCE, "%.1f %% du CA du mois n'est rattaché à aucun médecin.",
                            nz(t.getCaNonAffecteMois()) * 100.0 / caMois),
                    "/import-export-excel"));
        }

        sujets.sort(Comparator.comparing(s -> "CRITIQUE".equals(s.getNiveau()) ? 0 : 1));
        return sujets;
    }

    // ── Utilitaires ──────────────────────────────────────────────────────────

    private SujetArbitrage sujet(String niveau, String titre, String detail, String lien) {
        return SujetArbitrage.builder().niveau(niveau).titre(titre).detail(detail).lien(lien).build();
    }

    private long sommeCa(LocalDate debut, LocalDate fin) {
        return nz(extractionDonneesRepository.sumPrixAPayerByDateRange(debut, fin));
    }

    static Double evolutionPct(long valeur, long reference) {
        if (reference <= 0) return null;
        return arrondi1((valeur - reference) * 100.0 / reference);
    }

    private static double arrondi1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }

    private static String nomComplet(Medecin m) {
        String nom = m.getNom() == null ? "" : m.getNom();
        String prenom = m.getPrenom() == null ? "" : m.getPrenom();
        return (nom + " " + prenom).trim();
    }
}
