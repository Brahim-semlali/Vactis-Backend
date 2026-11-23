package com.vactis.service;

import com.vactis.dto.suiviequipe.SuiviEquipeDtos.CommercialResume;
import com.vactis.dto.suiviequipe.SuiviEquipeDtos.ComparaisonVisite;
import com.vactis.dto.suiviequipe.SuiviEquipeDtos.EvolutionMedecin;
import com.vactis.dto.suiviequipe.SuiviEquipeDtos.MoisActivite;
import com.vactis.dto.suiviequipe.SuiviEquipeDtos.ReclamationLigne;
import com.vactis.dto.suiviequipe.SuiviEquipeDtos.VisiteLigne;
import com.vactis.model.action.Action;
import com.vactis.model.data.ExtractionDonnees;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.QualificationVisite;
import com.vactis.model.medecin.RetourTerrain;
import com.vactis.model.medecin.StatutVisite;
import com.vactis.model.reclamation.Reclamation;
import com.vactis.repository.ActionRepository;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.ReclamationRepository;
import com.vactis.repository.RetourTerrainRepository;
import com.vactis.service.Activite.ActivitePortefeuilleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Suivi équipe : historique complet par commercial (visites, qualifications, commentaires,
 * réclamations) et évolution observée des médecins après visite.
 */
@Service
@RequiredArgsConstructor
public class SuiviEquipeService {

    static final String NON_RENSEIGNE = "Non renseigné";
    static final String AVERTISSEMENT = "Une évolution observée après une visite ne prouve pas que la visite en est la cause.";
    private static final int FENETRE_MOIS = 3;
    private static final int JOURS_VISITE_REPETEE = 30;
    private static final int MOIS_MAX_CHRONOLOGIE = 24;
    private static final double SEUIL_TENDANCE_PCT = 10.0;

    private final RetourTerrainRepository retourTerrainRepository;
    private final ActionRepository actionRepository;
    private final ReclamationRepository reclamationRepository;
    private final MedecinRepository medecinRepository;
    private final ExtractionDonneesRepository extractionDonneesRepository;
    private final ActivitePortefeuilleService portefeuilleService;

    // ── Synthèse par commercial ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CommercialResume> getCommerciaux(LocalDate debut, LocalDate fin) {
        Map<String, Agregat> agregats = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (Action a : actionRepository.findAll()) {
            if (estVide(a.getCommercial()) || !dansPeriode(a.getDateVisite(), debut, fin)) continue;
            Agregat ag = agregats.computeIfAbsent(a.getCommercial().trim(), Agregat::new);
            ag.actionsPrevues++;
            if (Boolean.TRUE.equals(a.getEstEnRetard())) ag.actionsEnRetard++;
        }
        for (RetourTerrain r : retours(debut, fin)) {
            Agregat ag = agregats.computeIfAbsent(commercial(r), Agregat::new);
            if (estNonRealisee(r)) {
                ag.visitesNonRealisees++;
                continue;
            }
            ag.visitesRealisees++;
            if (r.getAction() != null) ag.visitesVactis++; else ag.visitesLibres++;
            switch (qualification(r)) {
                case FAVORABLE -> ag.favorables++;
                case DEFAVORABLE -> ag.defavorables++;
                case NEUTRE -> ag.neutres++;
                default -> ag.nonRenseignees++;
            }
            if (r.getMedecin() != null) ag.medecins.add(r.getMedecin().getId());
            if (r.getDateVisite() != null && (ag.derniereVisite == null || r.getDateVisite().isAfter(ag.derniereVisite))) {
                ag.derniereVisite = r.getDateVisite();
            }
        }
        for (Reclamation rec : reclamationRepository.findAll()) {
            if (estVide(rec.getCommercialDeclarant()) || !dansPeriode(rec.getDateDeclaration(), debut, fin)) continue;
            Agregat ag = agregats.computeIfAbsent(rec.getCommercialDeclarant().trim(), Agregat::new);
            ag.reclamations++;
            if (estOuverte(rec)) ag.reclamationsOuvertes++;
        }

        return agregats.values().stream().map(Agregat::toResume).toList();
    }

    // ── Historique des visites ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<VisiteLigne> getVisites(String commercial, LocalDate debut, LocalDate fin, String type, String statut) {
        Map<Long, List<LocalDate>> datesRealiseesParMedecin = datesRealiseesParMedecin(retourTerrainRepository.findAll());
        return retours(debut, fin).stream()
                .filter(r -> estVide(commercial) || commercial(r).equalsIgnoreCase(commercial.trim()))
                .filter(r -> estVide(type) || typeVisite(r).equalsIgnoreCase(type.trim()))
                .filter(r -> estVide(statut) || statutVisite(r).equalsIgnoreCase(statut.trim()))
                .sorted(Comparator.comparing(RetourTerrain::getDateVisite, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(r -> toVisiteLigne(r, datesRealiseesParMedecin))
                .toList();
    }

    // ── Réclamations ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ReclamationLigne> getReclamations(String commercial, LocalDate debut, LocalDate fin) {
        LocalDate aujourdhui = LocalDate.now();
        return reclamationRepository.findAll().stream()
                .filter(r -> dansPeriode(r.getDateDeclaration(), debut, fin))
                .filter(r -> estVide(commercial)
                        || (r.getCommercialDeclarant() != null && r.getCommercialDeclarant().trim().equalsIgnoreCase(commercial.trim())))
                .sorted(Comparator.comparing(Reclamation::getDateDeclaration, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(r -> ReclamationLigne.builder()
                        .id(r.getId())
                        .dateDeclaration(r.getDateDeclaration())
                        .dateVisite(r.getDateVisite())
                        .medecinId(r.getMedecin() == null ? null : r.getMedecin().getId())
                        .medecinNom(nomMedecin(r.getMedecin()))
                        .commercial(estVide(r.getCommercialDeclarant()) ? NON_RENSEIGNE : r.getCommercialDeclarant())
                        .categorie(r.getCategorie() == null ? null : r.getCategorie().name())
                        .priorite(r.getPriorite() == null ? null : r.getPriorite().name())
                        .statut(r.getStatut() == null ? null : r.getStatut().name())
                        .ouverte(estOuverte(r))
                        .responsable(r.getResponsableTraitement())
                        .dateEcheance(r.getDateEcheance())
                        .enRetard(estOuverte(r) && r.getDateEcheance() != null && r.getDateEcheance().isBefore(aujourdhui))
                        .description(r.getDescription())
                        .prochaineInstruction(r.getProchaineInstruction())
                        .build())
                .toList();
    }

    // ── Évolution observée d'un médecin ──────────────────────────────────────

    @Transactional(readOnly = true)
    public EvolutionMedecin getEvolutionMedecin(Long medecinId) {
        Medecin medecin = medecinRepository.findById(medecinId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Médecin introuvable"));

        List<RetourTerrain> visites = new ArrayList<>(retourTerrainRepository.findByMedecinOrderByDateVisiteDescCreatedAtDesc(medecin));
        visites.sort(Comparator.comparing(RetourTerrain::getDateVisite, Comparator.nullsLast(Comparator.naturalOrder())));
        List<ExtractionDonnees> dossiers = extractionDonneesRepository.findByMedecinIdOrderByDateReceptionAsc(medecinId);

        YearMonth dernierMoisDonnees = dernierMoisDisponible();
        Map<YearMonth, long[]> activite = activiteParMois(dossiers);
        List<YearMonth> plage = plageMois(visites, activite, dernierMoisDonnees);
        Map<YearMonth, String> statuts = statutsParMois(medecin, plage, dernierMoisDonnees);

        Map<YearMonth, Integer> visitesParMois = new HashMap<>();
        visites.stream().filter(v -> v.getDateVisite() != null)
                .forEach(v -> visitesParMois.merge(YearMonth.from(v.getDateVisite()), 1, Integer::sum));

        List<MoisActivite> mois = plage.stream()
                .map(ym -> MoisActivite.builder()
                        .mois(ym.toString())
                        .ca(activite.getOrDefault(ym, new long[2])[0])
                        .cas(activite.getOrDefault(ym, new long[2])[1])
                        .statut(statuts.get(ym))
                        .visites(visitesParMois.getOrDefault(ym, 0))
                        .build())
                .toList();

        List<RetourTerrain> realisees = visites.stream()
                .filter(v -> v.getDateVisite() != null && !estNonRealisee(v))
                .toList();
        List<ComparaisonVisite> comparaisons = realisees.stream()
                .map(v -> comparer(v, realisees, activite, statuts, dernierMoisDonnees))
                .toList();

        Map<Long, List<LocalDate>> datesRealisees = datesRealiseesParMedecin(visites);
        return EvolutionMedecin.builder()
                .medecinId(medecin.getId())
                .medecinNom(nomMedecin(medecin))
                .specialite(medecin.getSpecialite())
                .segment(medecin.getSegment())
                .statutActuel(medecin.getStatutPilotage() == null ? null : medecin.getStatutPilotage().name())
                .dernierMoisDisponible(dernierMoisDonnees == null ? null : dernierMoisDonnees.toString())
                .visites(visites.stream().map(v -> toVisiteLigne(v, datesRealisees)).toList())
                .mois(mois)
                .comparaisons(comparaisons)
                .avertissement(AVERTISSEMENT)
                .build();
    }

    ComparaisonVisite comparer(RetourTerrain visite, List<RetourTerrain> realisees, Map<YearMonth, long[]> activite,
                               Map<YearMonth, String> statuts, YearMonth dernierMoisDonnees) {
        YearMonth m = YearMonth.from(visite.getDateVisite());
        List<YearMonth> avant = new ArrayList<>();
        List<YearMonth> apres = new ArrayList<>();
        for (int i = 1; i <= FENETRE_MOIS; i++) {
            avant.add(m.minusMonths(i));
            YearMonth suivant = m.plusMonths(i);
            if (dernierMoisDonnees != null && !suivant.isAfter(dernierMoisDonnees)) apres.add(suivant);
        }
        // Les mois "avant" ne comptent que s'ils sont couverts par les données du laboratoire
        YearMonth premierMois = activite.keySet().stream().min(Comparator.naturalOrder()).orElse(null);
        avant.removeIf(ym -> premierMois == null || ym.isBefore(premierMois)
                || (dernierMoisDonnees != null && ym.isAfter(dernierMoisDonnees)));

        Double caAvant = moyenne(avant, activite, 0);
        Double caApres = moyenne(apres, activite, 0);
        boolean insuffisant = avant.isEmpty() || apres.isEmpty();
        Double evolution = (caAvant == null || caApres == null || caAvant <= 0)
                ? null : Math.round((caApres - caAvant) * 1000.0 / caAvant) / 10.0;

        LocalDate finFenetre = m.plusMonths(FENETRE_MOIS).atEndOfMonth();
        boolean repetees = realisees.stream().anyMatch(autre -> autre != visite
                && autre.getDateVisite().isAfter(visite.getDateVisite())
                && !autre.getDateVisite().isAfter(finFenetre));

        return ComparaisonVisite.builder()
                .visiteId(visite.getId())
                .dateVisite(visite.getDateVisite())
                .commercial(commercial(visite))
                .qualification(qualification(visite).name())
                .caMoyenAvant(caAvant)
                .casMoyenAvant(moyenne(avant, activite, 1))
                .moisAvantDisponibles(avant.size())
                .caMoyenApres(caApres)
                .casMoyenApres(moyenne(apres, activite, 1))
                .moisApresDisponibles(apres.size())
                .evolutionCaPct(evolution)
                .caMPlus1(valeurSiDisponible(m.plusMonths(1), activite, dernierMoisDonnees))
                .caMPlus3(valeurSiDisponible(m.plusMonths(3), activite, dernierMoisDonnees))
                .statutAvant(statuts.get(m))
                .statutApres(apres.isEmpty() ? null : statuts.get(m.plusMonths(1)))
                .tendance(insuffisant ? "NON_OBSERVABLE" : tendance(caAvant, caApres))
                .donneesInsuffisantes(insuffisant)
                .visitesRepetees(repetees)
                .build();
    }

    static String tendance(Double avant, Double apres) {
        if (avant == null || apres == null) return "NON_OBSERVABLE";
        if (avant <= 0) return apres > 0 ? "EN_HAUSSE" : "STABLE";
        double pct = (apres - avant) * 100.0 / avant;
        if (pct >= SEUIL_TENDANCE_PCT) return "EN_HAUSSE";
        if (pct <= -SEUIL_TENDANCE_PCT) return "EN_BAISSE";
        return "STABLE";
    }

    // ── Construction de la chronologie ───────────────────────────────────────

    private Map<YearMonth, long[]> activiteParMois(List<ExtractionDonnees> dossiers) {
        Map<YearMonth, long[]> activite = new TreeMap<>();
        for (ExtractionDonnees d : dossiers) {
            if (d.getDateReception() == null) continue;
            long[] valeurs = activite.computeIfAbsent(YearMonth.from(d.getDateReception()), k -> new long[2]);
            valeurs[0] += d.getPrixAPayer() == null ? 0 : d.getPrixAPayer();
            valeurs[1] += d.getNombreAnalyses() == null ? 0 : d.getNombreAnalyses();
        }
        return activite;
    }

    private List<YearMonth> plageMois(List<RetourTerrain> visites, Map<YearMonth, long[]> activite, YearMonth dernierMoisDonnees) {
        List<YearMonth> bornes = new ArrayList<>(activite.keySet());
        visites.stream().map(RetourTerrain::getDateVisite).filter(Objects::nonNull).map(YearMonth::from).forEach(bornes::add);
        if (bornes.isEmpty()) return List.of();
        YearMonth debut = bornes.stream().min(Comparator.naturalOrder()).get();
        YearMonth fin = bornes.stream().max(Comparator.naturalOrder()).get();
        if (dernierMoisDonnees != null && dernierMoisDonnees.isAfter(fin)) fin = dernierMoisDonnees;
        if (ChronoUnit.MONTHS.between(debut, fin) >= MOIS_MAX_CHRONOLOGIE) debut = fin.minusMonths(MOIS_MAX_CHRONOLOGIE - 1L);
        List<YearMonth> plage = new ArrayList<>();
        for (YearMonth ym = debut; !ym.isAfter(fin); ym = ym.plusMonths(1)) plage.add(ym);
        return plage;
    }

    private Map<YearMonth, String> statutsParMois(Medecin medecin, List<YearMonth> plage, YearMonth dernierMoisDonnees) {
        Map<YearMonth, String> statuts = new LinkedHashMap<>();
        for (YearMonth ym : plage) {
            if (dernierMoisDonnees != null && ym.isAfter(dernierMoisDonnees)) continue;
            String statut = portefeuilleService.buildStatutMapForMonth(ym, List.of(medecin)).get(medecin.getId());
            if (statut != null) statuts.put(ym, statut);
        }
        return statuts;
    }

    private YearMonth dernierMoisDisponible() {
        List<LocalDate> dates = extractionDonneesRepository.findAllDatesDescending();
        return dates == null || dates.isEmpty() ? null : YearMonth.from(dates.get(0));
    }

    private static Double moyenne(List<YearMonth> mois, Map<YearMonth, long[]> activite, int index) {
        if (mois.isEmpty()) return null;
        double total = mois.stream().mapToLong(ym -> activite.getOrDefault(ym, new long[2])[index]).sum();
        return Math.round(total / mois.size() * 10.0) / 10.0;
    }

    private static Long valeurSiDisponible(YearMonth ym, Map<YearMonth, long[]> activite, YearMonth dernierMoisDonnees) {
        if (dernierMoisDonnees == null || ym.isAfter(dernierMoisDonnees)) return null;
        return activite.getOrDefault(ym, new long[2])[0];
    }

    // ── Utilitaires ──────────────────────────────────────────────────────────

    private List<RetourTerrain> retours(LocalDate debut, LocalDate fin) {
        if (debut != null && fin != null) return retourTerrainRepository.findByDateVisiteBetweenWithFetch(debut, fin);
        return retourTerrainRepository.findAll().stream().filter(r -> dansPeriode(r.getDateVisite(), debut, fin)).toList();
    }

    private Map<Long, List<LocalDate>> datesRealiseesParMedecin(List<RetourTerrain> retours) {
        return retours.stream()
                .filter(r -> r.getMedecin() != null && r.getDateVisite() != null && !estNonRealisee(r))
                .collect(Collectors.groupingBy(r -> r.getMedecin().getId(),
                        Collectors.mapping(RetourTerrain::getDateVisite, Collectors.toList())));
    }

    private VisiteLigne toVisiteLigne(RetourTerrain r, Map<Long, List<LocalDate>> datesRealiseesParMedecin) {
        boolean repetee = false;
        if (r.getMedecin() != null && r.getDateVisite() != null && !estNonRealisee(r)) {
            repetee = datesRealiseesParMedecin.getOrDefault(r.getMedecin().getId(), List.of()).stream()
                    .filter(d -> !d.equals(r.getDateVisite()))
                    .anyMatch(d -> Math.abs(ChronoUnit.DAYS.between(d, r.getDateVisite())) <= JOURS_VISITE_REPETEE);
        }
        return VisiteLigne.builder()
                .id(r.getId())
                .dateVisite(r.getDateVisite())
                .commercial(commercial(r))
                .medecinId(r.getMedecin() == null ? null : r.getMedecin().getId())
                .medecinNom(estVide(r.getNomMedecin()) ? nomMedecin(r.getMedecin()) : r.getNomMedecin())
                .typeVisite(typeVisite(r))
                .vactis(r.getAction() != null)
                .statutVisite(statutVisite(r))
                .qualification(qualification(r).name())
                .commentaire(r.getCommentaire())
                .motifNonRealisation(r.getMotifNonRealisation())
                .reclamation(Boolean.TRUE.equals(r.getReclamation()))
                .prochaineAction(r.getProchaineAction())
                .dateProchaineAction(r.getDateProchaineAction())
                .visiteRepetee(repetee)
                .build();
    }

    static boolean dansPeriode(LocalDate date, LocalDate debut, LocalDate fin) {
        if (debut == null && fin == null) return true;
        if (date == null) return false;
        return (debut == null || !date.isBefore(debut)) && (fin == null || !date.isAfter(fin));
    }

    private static boolean estOuverte(Reclamation r) {
        return r.getStatut() == null || !DashboardDirectionService.STATUTS_FERMES.contains(r.getStatut());
    }

    private static boolean estNonRealisee(RetourTerrain r) {
        return r.getStatutVisite() == StatutVisite.NON_REALISEE;
    }

    private static String statutVisite(RetourTerrain r) {
        return r.getStatutVisite() == null ? StatutVisite.REALISEE.name() : r.getStatutVisite().name();
    }

    private static String typeVisite(RetourTerrain r) {
        return r.getTypeVisite() == null ? "NON_RENSEIGNE" : r.getTypeVisite().name();
    }

    private static QualificationVisite qualification(RetourTerrain r) {
        return r.getQualification() == null ? QualificationVisite.NON_RENSEIGNE : r.getQualification();
    }

    private static String commercial(RetourTerrain r) {
        return estVide(r.getVisiteur()) ? NON_RENSEIGNE : r.getVisiteur().trim();
    }

    private static boolean estVide(String s) {
        return s == null || s.isBlank();
    }

    private static String nomMedecin(Medecin m) {
        if (m == null) return "—";
        String nom = m.getNom() == null ? "" : m.getNom();
        String prenom = m.getPrenom() == null ? "" : m.getPrenom();
        String complet = (nom + " " + prenom).trim();
        return complet.isEmpty() ? "—" : complet;
    }

    /** Compteurs mutables d'un commercial, convertis en {@link CommercialResume}. */
    private static final class Agregat {
        private final String commercial;
        private long actionsPrevues;
        private long actionsEnRetard;
        private long visitesRealisees;
        private long visitesNonRealisees;
        private long visitesVactis;
        private long visitesLibres;
        private long favorables;
        private long defavorables;
        private long neutres;
        private long nonRenseignees;
        private long reclamations;
        private long reclamationsOuvertes;
        private final Set<Long> medecins = new HashSet<>();
        private LocalDate derniereVisite;

        private Agregat(String commercial) {
            this.commercial = commercial;
        }

        private CommercialResume toResume() {
            return CommercialResume.builder()
                    .commercial(commercial)
                    .actionsPrevues(actionsPrevues)
                    .actionsEnRetard(actionsEnRetard)
                    .visitesRealisees(visitesRealisees)
                    .visitesNonRealisees(visitesNonRealisees)
                    .visitesVactis(visitesVactis)
                    .visitesLibres(visitesLibres)
                    .qualificationsFavorables(favorables)
                    .qualificationsDefavorables(defavorables)
                    .qualificationsNeutres(neutres)
                    .qualificationsNonRenseignees(nonRenseignees)
                    .medecinsVisites(medecins.size())
                    .reclamationsDeclarees(reclamations)
                    .reclamationsOuvertes(reclamationsOuvertes)
                    .derniereVisite(derniereVisite)
                    .build();
        }
    }
}
