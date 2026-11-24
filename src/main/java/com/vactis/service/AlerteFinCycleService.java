package com.vactis.service;

import com.vactis.dto.alerte.AlertesFinCycleResponse;
import com.vactis.dto.alerte.AlertesFinCycleResponse.Alerte;
import com.vactis.dto.alerte.AlertesFinCycleResponse.LigneCommercial;
import com.vactis.model.action.Action;
import com.vactis.model.action.EtatAction;
import com.vactis.model.auth.Users;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.QualificationVisite;
import com.vactis.model.medecin.RetourTerrain;
import com.vactis.model.medecin.StatutVisite;
import com.vactis.model.medecin.TypeVisite;
import com.vactis.model.reclamation.Reclamation;
import com.vactis.model.reclamation.StatutReclamation;
import com.vactis.repository.ActionRepository;
import com.vactis.repository.ReclamationRepository;
import com.vactis.repository.RetourTerrainRepository;
import com.vactis.repository.auth.UserRepository;
import com.vactis.service.Activite.ActiviteEquipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Alertes de fin de cycle (voir {@link AlertesFinCycleResponse} pour les définitions).
 * Un commercial ne reçoit que ses propres alertes ; la direction voit toute l'équipe.
 */
@Service
@RequiredArgsConstructor
public class AlerteFinCycleService {

    /** Les alertes remontent dans la cloche à partir de J-7. */
    public static final int FENETRE_JOURS = 7;
    /** Elles passent en « urgent » à partir de J-3. */
    public static final int URGENCE_JOURS = 3;
    /** Une réclamation non traitée depuis 7 jours est signalée. */
    public static final int RECLAMATION_ATTENTE_JOURS = 7;
    /** Taux de réalisation des actions du cycle en dessous duquel la direction est alertée. */
    public static final int SEUIL_REALISATION = 60;

    static final String URGENT = "URGENT";
    static final String ATTENTION = "ATTENTION";
    static final String NON_RENSEIGNE = "Non renseigné";

    private static final Set<StatutReclamation> RECLAMATIONS_OUVERTES = Set.of(
            StatutReclamation.A_TRAITER, StatutReclamation.EN_COURS,
            StatutReclamation.EN_ATTENTE_INFORMATION, StatutReclamation.OUVERTE);

    private final ActionRepository actionRepository;
    private final RetourTerrainRepository retourTerrainRepository;
    private final ReclamationRepository reclamationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public AlertesFinCycleResponse getAlertes(String username, boolean vueEquipe, LocalDate aujourdhui) {
        Set<String> identites = vueEquipe ? null : identites(username);
        List<Action> actions = actionRepository.findAll();
        String cycle = ActiviteEquipeService.cycleActif(actions);
        YearMonth mois = parseCycle(cycle);

        AlertesFinCycleResponse.AlertesFinCycleResponseBuilder reponse = AlertesFinCycleResponse.builder()
                .cycle(cycle)
                .vueEquipe(vueEquipe)
                .fenetreJours(FENETRE_JOURS)
                .seuilRealisation(SEUIL_REALISATION);
        if (mois == null) {
            return reponse.alertes(List.of()).parCommercial(List.of()).build();
        }

        LocalDate cloture = mois.atEndOfMonth();
        long joursRestants = ChronoUnit.DAYS.between(aujourdhui, cloture);
        boolean urgent = joursRestants <= URGENCE_JOURS;

        List<RetourTerrain> retours = retourTerrainRepository.findAllWithFetch();
        Set<Long> actionsVisitees = new HashSet<>();
        retours.stream().filter(r -> r.getAction() != null).forEach(r -> actionsVisitees.add(r.getAction().getId()));

        List<Action> actionsCycle = actions.stream().filter(a -> cycle.equals(a.getCycleMensuel())).toList();
        List<Action> nonVisitees = actionsCycle.stream()
                .filter(a -> a.getEtatAction() != EtatAction.REALISEE && !actionsVisitees.contains(a.getId()))
                .toList();
        List<RetourTerrain> aQualifier = retours.stream()
                .filter(r -> r.getDateVisite() != null && YearMonth.from(r.getDateVisite()).equals(mois))
                .filter(AlerteFinCycleService::retourAQualifier)
                .toList();
        List<Reclamation> enAttente = reclamationRepository.findAll().stream()
                .filter(r -> r.getStatut() != null && RECLAMATIONS_OUVERTES.contains(r.getStatut()))
                .filter(r -> r.getDateDeclaration() != null
                        && !r.getDateDeclaration().isAfter(aujourdhui.minusDays(RECLAMATION_ATTENTE_JOURS)))
                .toList();

        List<Alerte> alertes = new ArrayList<>();
        nonVisitees.stream()
                .filter(a -> concerne(commercialAction(a), identites))
                .sorted(Comparator.comparing(Action::getDateEcheance, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Action::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(a -> alertes.add(Alerte.builder()
                        .type("ACTION_NON_VISITEE")
                        .gravite(urgent || echue(a.getDateEcheance(), aujourdhui) ? URGENT : ATTENTION)
                        .commercial(commercialAction(a))
                        .medecinId(a.getMedecin() == null ? null : a.getMedecin().getId())
                        .medecinNom(nomMedecin(a.getMedecin()))
                        .actionId(a.getId())
                        .libelle("Action non visitée")
                        .detail(libelleAction(a.getActionRecommandee()))
                        .date(a.getDateEcheance())
                        .build()));
        aQualifier.stream()
                .filter(r -> concerne(visiteur(r), identites))
                .sorted(Comparator.comparing(RetourTerrain::getDateVisite))
                .forEach(r -> alertes.add(Alerte.builder()
                        .type("RETOUR_A_QUALIFIER")
                        .gravite(urgent ? URGENT : ATTENTION)
                        .commercial(visiteur(r))
                        .medecinId(r.getMedecin() == null ? null : r.getMedecin().getId())
                        .medecinNom(nomMedecin(r.getMedecin()))
                        .actionId(r.getAction() == null ? null : r.getAction().getId())
                        .retourTerrainId(r.getId())
                        .libelle("Retour à qualifier")
                        .detail(r.getAction() == null ? "Visite hors VACTIS" : "Visite VACTIS")
                        .date(r.getDateVisite())
                        .build()));
        enAttente.stream()
                .filter(r -> identites == null || concerne(r.getCommercialDeclarant(), identites))
                .sorted(Comparator.comparing(Reclamation::getDateDeclaration))
                .forEach(r -> alertes.add(Alerte.builder()
                        .type("RECLAMATION_EN_ATTENTE")
                        .gravite(echue(r.getDateEcheance(), aujourdhui) ? URGENT : ATTENTION)
                        .commercial(estVide(r.getCommercialDeclarant()) ? NON_RENSEIGNE : r.getCommercialDeclarant().trim())
                        .medecinId(r.getMedecin() == null ? null : r.getMedecin().getId())
                        .medecinNom(nomMedecin(r.getMedecin()))
                        .reclamationId(r.getId())
                        .libelle("Réclamation en attente")
                        .detail("Ouverte depuis " + ChronoUnit.DAYS.between(r.getDateDeclaration(), aujourdhui) + " jours")
                        .date(r.getDateDeclaration())
                        .build()));

        List<LigneCommercial> parCommercial = vueEquipe
                ? parCommercial(actionsCycle, actionsVisitees, aQualifier, enAttente)
                : List.of();
        parCommercial.stream()
                .filter(l -> l.getActionsDuCycle() > 0 && l.getTauxRealisation() < SEUIL_REALISATION)
                .forEach(l -> alertes.add(Alerte.builder()
                        .type("REALISATION_FAIBLE")
                        .gravite(urgent ? URGENT : ATTENTION)
                        .commercial(l.getCommercial())
                        .libelle("Réalisation faible")
                        .detail(l.getActionsVisitees() + " / " + l.getActionsDuCycle() + " actions visitées ("
                                + Math.round(l.getTauxRealisation()) + " %)")
                        .build()));

        alertes.sort(Comparator.comparing((Alerte a) -> URGENT.equals(a.getGravite()) ? 0 : 1));
        return reponse
                .dateCloture(cloture)
                .joursRestants(joursRestants)
                .fenetreActive(joursRestants <= FENETRE_JOURS)
                .urgent(urgent)
                .alertes(alertes)
                .parCommercial(parCommercial)
                .build();
    }

    private static List<LigneCommercial> parCommercial(List<Action> actionsCycle, Set<Long> actionsVisitees,
                                                       List<RetourTerrain> aQualifier, List<Reclamation> enAttente) {
        Map<String, String> noms = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        actionsCycle.forEach(a -> noms.putIfAbsent(commercialAction(a), commercialAction(a)));
        aQualifier.forEach(r -> noms.putIfAbsent(visiteur(r), visiteur(r)));
        return noms.values().stream().map(nom -> {
            List<Action> siennes = actionsCycle.stream().filter(a -> memeNom(commercialAction(a), nom)).toList();
            long visitees = siennes.stream()
                    .filter(a -> a.getEtatAction() == EtatAction.REALISEE || actionsVisitees.contains(a.getId()))
                    .count();
            return LigneCommercial.builder()
                    .commercial(nom)
                    .actionsDuCycle(siennes.size())
                    .actionsVisitees(visitees)
                    .actionsNonVisitees(siennes.size() - visitees)
                    .tauxRealisation(siennes.isEmpty() ? 0 : Math.round(visitees * 1000.0 / siennes.size()) / 10.0)
                    .retoursAQualifier(aQualifier.stream().filter(r -> memeNom(visiteur(r), nom)).count())
                    .reclamationsEnAttente(enAttente.stream().filter(r -> memeNom(r.getCommercialDeclarant(), nom)).count())
                    .build();
        }).toList();
    }

    // ── Règles ───────────────────────────────────────────────────────────────

    /** Échange réalisé (ni non réalisé, ni passage de service) dont la qualification n'a pas été saisie. */
    static boolean retourAQualifier(RetourTerrain r) {
        if (r.getStatutVisite() == StatutVisite.NON_REALISEE) return false;
        if (r.getAction() == null && r.getTypeVisite() == TypeVisite.SERVICE) return false;
        return r.getQualification() == null || r.getQualification() == QualificationVisite.NON_RENSEIGNE;
    }

    /** Noms sous lesquels l'utilisateur peut apparaître comme commercial (identifiant, prénom nom, nom prénom). */
    Set<String> identites(String username) {
        Set<String> noms = new LinkedHashSet<>();
        if (estVide(username)) return noms;
        noms.add(normaliser(username));
        userRepository.findByUsername(username).ifPresent((Users u) -> {
            String prenom = u.getFirstName() == null ? "" : u.getFirstName().trim();
            String nom = u.getLastName() == null ? "" : u.getLastName().trim();
            if (!prenom.isEmpty() && !nom.isEmpty()) {
                noms.add(normaliser(prenom + " " + nom));
                noms.add(normaliser(nom + " " + prenom));
            }
        });
        return noms;
    }

    private static boolean concerne(String commercial, Set<String> identites) {
        return identites == null || (!estVide(commercial) && identites.contains(normaliser(commercial)));
    }

    private static String commercialAction(Action a) {
        if (!estVide(a.getCommercial())) return a.getCommercial().trim();
        if (!estVide(a.getReservedBy())) return a.getReservedBy().trim();
        return NON_RENSEIGNE;
    }

    private static String visiteur(RetourTerrain r) {
        return estVide(r.getVisiteur()) ? NON_RENSEIGNE : r.getVisiteur().trim();
    }

    private static boolean echue(LocalDate echeance, LocalDate aujourdhui) {
        return echeance != null && echeance.isBefore(aujourdhui);
    }

    static YearMonth parseCycle(String cycle) {
        if (estVide(cycle)) return null;
        try {
            return YearMonth.parse(cycle.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String libelleAction(String code) {
        if (estVide(code)) return "Action VACTIS";
        String texte = code.trim().replace('_', ' ').toLowerCase(Locale.FRENCH);
        return Character.toUpperCase(texte.charAt(0)) + texte.substring(1);
    }

    private static String nomMedecin(Medecin m) {
        if (m == null) return "—";
        String complet = ((m.getNom() == null ? "" : m.getNom()) + " " + (m.getPrenom() == null ? "" : m.getPrenom())).trim();
        return complet.isEmpty() ? "—" : complet;
    }

    private static boolean memeNom(String a, String b) {
        return a != null && b != null && normaliser(a).equals(normaliser(b));
    }

    private static String normaliser(String s) {
        return s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static boolean estVide(String s) {
        return s == null || s.isBlank();
    }
}
