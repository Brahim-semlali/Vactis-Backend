package com.vactis.service.Activite;

import com.vactis.dto.activite.EquipeCommercialeResponse;
import com.vactis.dto.activite.EquipeCommercialeResponse.ActionSansVisite;
import com.vactis.dto.activite.EquipeCommercialeResponse.Decisions;
import com.vactis.dto.activite.EquipeCommercialeResponse.LigneCommercial;
import com.vactis.dto.activite.EquipeCommercialeResponse.LigneVisite;
import com.vactis.dto.activite.EquipeCommercialeResponse.Synthese;
import com.vactis.model.action.Action;
import com.vactis.model.action.EtatAction;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.QualificationVisite;
import com.vactis.model.medecin.RetourTerrain;
import com.vactis.model.medecin.StatutVisite;
import com.vactis.model.medecin.TypeVisite;
import com.vactis.repository.ActionRepository;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.RetourTerrainRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Lecture activité → onglet « Équipe commerciale » : travail confié et terrain parcouru,
 * comparaison des commerciaux, qualité des échanges, évolution observée et suites à donner.
 * Les définitions des indicateurs sont documentées dans {@link EquipeCommercialeResponse}.
 */
@Service
@RequiredArgsConstructor
public class ActiviteEquipeService {

    static final String NON_RENSEIGNE = "Non renseigné";
    static final String AVERTISSEMENT = "Les évolutions comparent le statut du médecin le mois de la visite VACTIS et le mois suivant. "
            + "Elles ne démontrent pas que la visite a causé le changement.";

    private final RetourTerrainRepository retourTerrainRepository;
    private final ActionRepository actionRepository;
    private final ExtractionDonneesRepository extractionDonneesRepository;
    private final ActivitePortefeuilleService portefeuilleService;

    @Transactional(readOnly = true)
    public EquipeCommercialeResponse getEquipeCommerciale(LocalDate debut, LocalDate fin, String commercial) {
        String filtre = estVide(commercial) ? null : commercial.trim();
        List<RetourTerrain> tous = retourTerrainRepository.findAllWithFetch();
        List<Action> actions = actionRepository.findAll();

        List<RetourTerrain> periode = tous.stream()
                .filter(r -> r.getMedecin() != null && dansPeriode(r.getDateVisite(), debut, fin))
                .toList();
        Set<Long> medecinsConnus = new HashSet<>(extractionDonneesRepository.findDistinctMedecinIdsWithActivity());
        Map<Long, Observation> observations = observerVisitesVactis(periode);

        List<Passage> passages = periode.stream()
                .map(r -> Passage.de(r, medecinsConnus, observations.get(r.getId())))
                .toList();
        // Vue individuelle : passages dont le commercial est le visiteur principal
        List<Passage> principaux = passages.stream().filter(p -> filtre == null || memeCommercial(p.commercial, filtre)).toList();
        long appuisFiltre = passages.stream().filter(p -> p.realise && filtre != null && memeCommercial(p.appui, filtre)).count();

        String cycleActif = cycleActif(actions);
        return EquipeCommercialeResponse.builder()
                .debut(debut)
                .fin(fin)
                .commercial(filtre)
                .moisDisponibles(moisDisponibles(tous))
                .commerciaux(commerciaux(tous, actions, cycleActif))
                .synthese(synthese(principaux, filtre == null ? compter(passages, p -> p.realise && p.appui != null) : appuisFiltre))
                .parCommercial(parCommercial(passages))
                .qualifications(qualifications(principaux))
                .evolutions(evolutions(principaux))
                .visites(principaux.stream()
                        .sorted(Comparator.comparing((Passage p) -> p.date, Comparator.nullsLast(Comparator.reverseOrder())))
                        .map(Passage::toLigne)
                        .toList())
                .decisions(decisions(principaux, actions, tous, cycleActif, filtre))
                .avertissement(AVERTISSEMENT)
                .build();
    }

    // ── Évolution observée (règle R4, visites VACTIS réalisées) ─────────────

    private Map<Long, Observation> observerVisitesVactis(List<RetourTerrain> retours) {
        Map<YearMonth, List<RetourTerrain>> parMois = new TreeMap<>();
        retours.stream()
                .filter(r -> r.getAction() != null && estRealise(r) && r.getDateVisite() != null)
                .forEach(r -> parMois.computeIfAbsent(YearMonth.from(r.getDateVisite()), k -> new ArrayList<>()).add(r));
        if (parMois.isEmpty()) return Map.of();

        Set<YearMonth> moisAvecDonnees = new HashSet<>();
        extractionDonneesRepository.findAllDatesDescending().stream()
                .filter(Objects::nonNull).map(YearMonth::from).forEach(moisAvecDonnees::add);
        YearMonth moisCourant = YearMonth.now();

        Map<Long, Observation> observations = new HashMap<>();
        parMois.forEach((mois, visites) -> {
            Map<Long, Medecin> parId = new LinkedHashMap<>();
            visites.forEach(r -> parId.putIfAbsent(r.getMedecin().getId(), r.getMedecin()));
            List<Medecin> medecins = new ArrayList<>(parId.values());
            YearMonth suivant = mois.plusMonths(1);
            // M+1 doit être clôturé et présent dans l'extraction pour que l'évolution soit lisible
            boolean apresCalculable = suivant.isBefore(moisCourant) && moisAvecDonnees.contains(suivant);
            Map<Long, String> statutsM = portefeuilleService.buildStatutMapForMonth(mois, medecins);
            Map<Long, String> statutsM1 = apresCalculable ? portefeuilleService.buildStatutMapForMonth(suivant, medecins) : Map.of();
            Set<Long> actifsM1 = apresCalculable
                    ? new HashSet<>(extractionDonneesRepository.findMedecinIdsWithActivityInRange(suivant.atDay(1), suivant.atEndOfMonth()))
                    : Set.of();
            for (RetourTerrain r : visites) {
                Long id = r.getMedecin().getId();
                String avant = statutsM.getOrDefault(id, EvolutionStatutPolicy.STATUT_PAR_DEFAUT);
                boolean observable = apresCalculable && actifsM1.contains(id);
                String apres = observable ? statutsM1.getOrDefault(id, EvolutionStatutPolicy.STATUT_PAR_DEFAUT) : null;
                observations.put(r.getId(), new Observation(avant, apres, EvolutionStatutPolicy.evolution(avant, apres, observable)));
            }
        });
        return observations;
    }

    // ── Blocs de la réponse ──────────────────────────────────────────────────

    private Synthese synthese(List<Passage> passages, long participationsAppui) {
        long saisis = passages.size();
        long realises = compter(passages, p -> p.realise);
        return Synthese.builder()
                .passagesSaisis(saisis)
                .passagesRealises(realises)
                .tauxRealisation(saisis == 0 ? 0 : Math.round(realises * 1000.0 / saisis) / 10.0)
                .visitesVactis(compter(passages, p -> p.realise && p.vactis))
                .horsVactis(compter(passages, p -> p.realise && !p.vactis))
                .passagesService(compter(passages, p -> p.realise && p.service))
                .echangesCommerciaux(compter(passages, Passage::estEchange))
                .medecinsEnEchange(medecins(passages, false))
                .prospects(medecins(passages, true))
                .relais(compter(passages, p -> p.realise && p.relais))
                .participationsAppui(participationsAppui)
                .build();
    }

    private List<LigneCommercial> parCommercial(List<Passage> passages) {
        Map<String, String> noms = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        passages.forEach(p -> {
            noms.putIfAbsent(p.commercial, p.commercial);
            if (p.appui != null) noms.putIfAbsent(p.appui, p.appui);
        });
        return noms.values().stream().map(nom -> {
            List<Passage> siens = passages.stream().filter(p -> memeCommercial(p.commercial, nom)).toList();
            return LigneCommercial.builder()
                    .commercial(nom)
                    .passagesSaisis(siens.size())
                    .passagesPrincipaux(compter(siens, p -> p.realise))
                    .participationsAppui(compter(passages, p -> p.realise && memeCommercial(p.appui, nom)))
                    .visitesVactis(compter(siens, p -> p.realise && p.vactis))
                    .horsVactis(compter(siens, p -> p.realise && !p.vactis))
                    .services(compter(siens, p -> p.realise && p.service))
                    .echanges(compter(siens, Passage::estEchange))
                    .medecinsEnEchange(medecins(siens, false))
                    .prospects(medecins(siens, true))
                    .retoursQualifies(compter(siens, p -> p.estEchange() && p.qualification != QualificationVisite.NON_RENSEIGNE))
                    .retoursFavorables(compter(siens, p -> p.estEchange() && p.qualification == QualificationVisite.FAVORABLE))
                    .refusOuReclamations(compter(siens, p -> p.estEchange() && p.refusOuReclamation()))
                    .progressions(compter(siens, p -> EvolutionStatutPolicy.FAVORABLE.equals(p.evolution())))
                    .comparables(compter(siens, p -> p.evolution() != null && !EvolutionStatutPolicy.NON_OBSERVABLE.equals(p.evolution())))
                    .baisses(compter(siens, p -> EvolutionStatutPolicy.DEFAVORABLE.equals(p.evolution())))
                    .relais(compter(siens, p -> p.realise && p.relais))
                    .build();
        }).toList();
    }

    private Map<String, Long> qualifications(List<Passage> passages) {
        Map<String, Long> repartition = new LinkedHashMap<>();
        for (QualificationVisite q : List.of(QualificationVisite.FAVORABLE, QualificationVisite.NEUTRE, QualificationVisite.DEFAVORABLE,
                QualificationVisite.RECLAMATION, QualificationVisite.CONGE_ABSENCE, QualificationVisite.NON_RENSEIGNE)) {
            repartition.put(q.name(), compter(passages, p -> p.estEchange() && p.qualification == q));
        }
        return repartition;
    }

    private Map<String, Long> evolutions(List<Passage> passages) {
        Map<String, Long> repartition = new LinkedHashMap<>();
        for (String e : List.of(EvolutionStatutPolicy.FAVORABLE, EvolutionStatutPolicy.STABLE,
                EvolutionStatutPolicy.DEFAVORABLE, EvolutionStatutPolicy.NON_OBSERVABLE)) {
            repartition.put(e, compter(passages, p -> e.equals(p.evolution())));
        }
        return repartition;
    }

    private Decisions decisions(List<Passage> passages, List<Action> actions, List<RetourTerrain> tous,
                                String cycleActif, String filtre) {
        Set<Long> actionsVisitees = new HashSet<>();
        tous.stream().filter(r -> r.getAction() != null).forEach(r -> actionsVisitees.add(r.getAction().getId()));

        List<ActionSansVisite> sansVisite = cycleActif == null ? List.of() : actions.stream()
                .filter(a -> cycleActif.equals(a.getCycleMensuel()))
                .filter(a -> a.getEtatAction() != EtatAction.REALISEE && !actionsVisitees.contains(a.getId()))
                .filter(a -> filtre == null || memeCommercial(a.getCommercial(), filtre))
                .sorted(Comparator.comparing(Action::getDateEcheance, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Action::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(a -> ActionSansVisite.builder()
                        .actionId(a.getId())
                        .medecinId(a.getMedecin() == null ? null : a.getMedecin().getId())
                        .medecinNom(nomMedecin(a.getMedecin()))
                        .actionRecommandee(a.getActionRecommandee())
                        .commercial(estVide(a.getCommercial()) ? NON_RENSEIGNE : a.getCommercial().trim())
                        .urgence(a.getUrgence() == null ? null : a.getUrgence().name())
                        .dateEcheance(a.getDateEcheance())
                        .build())
                .toList();

        return Decisions.builder()
                .cycleActif(cycleActif)
                .actionsSansVisite(sansVisite)
                .refusOuReclamations(compter(passages, p -> p.estEchange() && p.refusOuReclamation()))
                .baissesObservees(passages.stream().filter(p -> EvolutionStatutPolicy.DEFAVORABLE.equals(p.evolution()))
                        .map(p -> p.medecinId).distinct().count())
                .retoursACompleter(compter(passages, p -> p.estEchange() && p.qualification == QualificationVisite.NON_RENSEIGNE))
                .build();
    }

    // ── Listes de référence ──────────────────────────────────────────────────

    private static List<String> moisDisponibles(List<RetourTerrain> retours) {
        return retours.stream().map(RetourTerrain::getDateVisite).filter(Objects::nonNull)
                .map(YearMonth::from).distinct().sorted(Comparator.reverseOrder()).map(YearMonth::toString).toList();
    }

    private static List<String> commerciaux(List<RetourTerrain> retours, List<Action> actions, String cycleActif) {
        Map<String, String> noms = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (RetourTerrain r : retours) {
            if (!estVide(r.getVisiteur())) noms.putIfAbsent(r.getVisiteur().trim(), r.getVisiteur().trim());
            if (!estVide(r.getCommercialAppui())) noms.putIfAbsent(r.getCommercialAppui().trim(), r.getCommercialAppui().trim());
        }
        for (Action a : actions) {
            if (cycleActif != null && cycleActif.equals(a.getCycleMensuel()) && !estVide(a.getCommercial())) {
                noms.putIfAbsent(a.getCommercial().trim(), a.getCommercial().trim());
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(noms.values()));
    }

    /** Classeur actif : le cycle mensuel le plus récent parmi les actions. */
    static String cycleActif(List<Action> actions) {
        return actions.stream().map(Action::getCycleMensuel).filter(c -> !estVide(c))
                .max(Comparator.naturalOrder()).orElse(null);
    }

    // ── Utilitaires ──────────────────────────────────────────────────────────

    private static long compter(List<Passage> passages, Predicate<Passage> critere) {
        return passages.stream().filter(critere).count();
    }

    private static long medecins(List<Passage> passages, boolean prospects) {
        return passages.stream().filter(p -> p.estEchange() && p.prospect == prospects)
                .map(p -> p.medecinId).filter(Objects::nonNull).distinct().count();
    }

    static boolean dansPeriode(LocalDate date, LocalDate debut, LocalDate fin) {
        if (debut == null && fin == null) return true;
        if (date == null) return false;
        return (debut == null || !date.isBefore(debut)) && (fin == null || !date.isAfter(fin));
    }

    private static boolean estRealise(RetourTerrain r) {
        return r.getStatutVisite() != StatutVisite.NON_REALISEE;
    }

    static boolean memeCommercial(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

    private static boolean estVide(String s) {
        return s == null || s.isBlank();
    }

    private static String nomMedecin(Medecin m) {
        if (m == null) return "—";
        String complet = ((m.getNom() == null ? "" : m.getNom()) + " " + (m.getPrenom() == null ? "" : m.getPrenom())).trim();
        return complet.isEmpty() ? "—" : complet;
    }

    private record Observation(String statutAvant, String statutApres, String evolution) {
    }

    /** Passage terrain normalisé selon les définitions de l'onglet. */
    private record Passage(RetourTerrain source, Long medecinId, LocalDate date, String commercial, String appui,
                           String prioritaire, boolean realise, boolean vactis, boolean service, boolean prospect,
                           boolean relais, QualificationVisite qualification, Observation observation) {

        static Passage de(RetourTerrain r, Set<Long> medecinsConnus, Observation observation) {
            Action action = r.getAction();
            String commercial = estVide(r.getVisiteur()) ? NON_RENSEIGNE : r.getVisiteur().trim();
            String prioritaire = action == null || estVide(action.getCommercial()) ? null : action.getCommercial().trim();
            boolean vactis = action != null;
            return new Passage(r, r.getMedecin().getId(), r.getDateVisite(), commercial,
                    estVide(r.getCommercialAppui()) ? null : r.getCommercialAppui().trim(),
                    prioritaire,
                    estRealise(r),
                    vactis,
                    !vactis && r.getTypeVisite() == TypeVisite.SERVICE,
                    !medecinsConnus.contains(r.getMedecin().getId()),
                    vactis && prioritaire != null && !memeCommercial(commercial, prioritaire),
                    r.getQualification() == null ? QualificationVisite.NON_RENSEIGNE : r.getQualification(),
                    observation);
        }

        boolean estEchange() {
            return realise && !service;
        }

        boolean refusOuReclamation() {
            return qualification == QualificationVisite.DEFAVORABLE || qualification == QualificationVisite.RECLAMATION
                    || Boolean.TRUE.equals(source.getReclamation());
        }

        String evolution() {
            return realise && observation != null ? observation.evolution() : null;
        }

        LigneVisite toLigne() {
            Action action = source.getAction();
            return LigneVisite.builder()
                    .retourTerrainId(source.getId())
                    .dateVisite(date)
                    .medecinId(medecinId)
                    .medecinNom(estVide(source.getNomMedecin()) ? nomMedecin(source.getMedecin()) : source.getNomMedecin())
                    .commercial(commercial)
                    .commercialAppui(appui)
                    .commercialPrioritaire(prioritaire)
                    .relais(relais)
                    .vactis(vactis)
                    .service(service)
                    .prospect(prospect)
                    .realisee(realise)
                    .typeVisite(source.getTypeVisite() == null ? null : source.getTypeVisite().name())
                    .actionRecommandee(action == null ? null : action.getActionRecommandee())
                    .qualification(qualification.name())
                    .reclamation(Boolean.TRUE.equals(source.getReclamation()) || qualification == QualificationVisite.RECLAMATION)
                    .statutAvant(observation == null ? null : observation.statutAvant())
                    .statutApres(observation == null ? null : observation.statutApres())
                    .evolution(evolution())
                    .commentaire(source.getCommentaire())
                    .motifNonRealisation(source.getMotifNonRealisation())
                    .build();
        }
    }
}
