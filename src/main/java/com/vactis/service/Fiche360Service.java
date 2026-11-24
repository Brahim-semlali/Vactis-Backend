package com.vactis.service;

import com.vactis.dto.medecin.Fiche360Response;
import com.vactis.dto.medecin.Fiche360Response.ActionLigne;
import com.vactis.dto.medecin.Fiche360Response.CommercialLigne;
import com.vactis.dto.medecin.Fiche360Response.Dossier;
import com.vactis.dto.medecin.Fiche360Response.Evenement;
import com.vactis.dto.medecin.Fiche360Response.Identite;
import com.vactis.dto.medecin.Fiche360Response.Indicateurs;
import com.vactis.dto.medecin.Fiche360Response.Mois;
import com.vactis.dto.medecin.Fiche360Response.ReclamationLigne;
import com.vactis.dto.medecin.Fiche360Response.Visite;
import com.vactis.dto.suiviequipe.SuiviEquipeDtos.MoisActivite;
import com.vactis.model.action.Action;
import com.vactis.model.action.EtatAction;
import com.vactis.model.alerte.AlerteHebdomadaire;
import com.vactis.model.data.ExtractionDonnees;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.QualificationVisite;
import com.vactis.model.medecin.RetourTerrain;
import com.vactis.model.medecin.StatutVisite;
import com.vactis.model.medecin.TypeVisite;
import com.vactis.model.reclamation.Reclamation;
import com.vactis.model.reclamation.StatutReclamation;
import com.vactis.repository.ActionRepository;
import com.vactis.repository.AlerteHebdomadaireRepository;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.ReclamationRepository;
import com.vactis.repository.RetourTerrainRepository;
import com.vactis.service.Activite.EvolutionStatutPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Assemble la fiche médecin 360° (voir {@link Fiche360Response}) à partir des données existantes :
 * activité mensuelle et statuts (même calcul que le Suivi équipe), visites, actions, réclamations,
 * alertes de silence et dossiers du laboratoire.
 */
@Service
@RequiredArgsConstructor
public class Fiche360Service {

    static final int NB_MOIS = 12;
    static final String POSITIF = "POSITIF";
    static final String NEGATIF = "NEGATIF";
    static final String NEUTRE = "NEUTRE";

    private static final Set<StatutReclamation> RECLAMATIONS_OUVERTES = Set.of(
            StatutReclamation.A_TRAITER, StatutReclamation.EN_COURS,
            StatutReclamation.EN_ATTENTE_INFORMATION, StatutReclamation.OUVERTE);

    private final MedecinRepository medecinRepository;
    private final SuiviEquipeService suiviEquipeService;
    private final RetourTerrainRepository retourTerrainRepository;
    private final ActionRepository actionRepository;
    private final ReclamationRepository reclamationRepository;
    private final ExtractionDonneesRepository extractionDonneesRepository;
    private final AlerteHebdomadaireRepository alerteHebdomadaireRepository;

    @Transactional(readOnly = true)
    public Fiche360Response getFiche(Long medecinId, LocalDate aujourdhui) {
        Medecin medecin = medecinRepository.findById(medecinId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Médecin introuvable"));

        List<MoisActivite> historique = suiviEquipeService.getEvolutionMedecin(medecinId).getMois();
        List<Mois> mois = (historique == null ? List.<MoisActivite>of() : historique).stream()
                .skip(Math.max(0, (historique == null ? 0 : historique.size()) - NB_MOIS))
                .map(m -> Mois.builder().mois(m.getMois()).ca(m.getCa()).dossiers(m.getCas())
                        .statut(m.getStatut()).visites(m.getVisites()).build())
                .toList();

        List<RetourTerrain> retours = retourTerrainRepository.findByMedecinOrderByDateVisiteDescCreatedAtDesc(medecin);
        List<Action> actions = new ArrayList<>(actionRepository.findByMedecinId(medecinId));
        actions.sort(Comparator.comparing(Action::getCycleMensuel, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Action::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        List<Reclamation> reclamations = reclamationRepository.findByMedecinOrderByDateDeclarationDesc(medecin);
        List<AlerteHebdomadaire> alertes = alerteHebdomadaireRepository.findByMedecinOrderByDateDetectionDesc(medecin);

        LocalDate debutFenetre = mois.isEmpty()
                ? YearMonth.from(aujourdhui).minusMonths(NB_MOIS - 1L).atDay(1)
                : YearMonth.parse(mois.get(0).getMois()).atDay(1);
        List<ExtractionDonnees> tousDossiers = extractionDonneesRepository.findByMedecinIdOrderByDateReceptionAsc(medecinId);
        List<Dossier> dossiers = tousDossiers.stream()
                .filter(d -> d.getDateReception() != null && !d.getDateReception().isBefore(debutFenetre))
                .sorted(Comparator.comparing(ExtractionDonnees::getDateReception).reversed())
                .map(Fiche360Service::toDossier)
                .toList();

        Set<Long> actionsVisitees = new HashSet<>();
        retours.stream().filter(r -> r.getAction() != null).forEach(r -> actionsVisitees.add(r.getAction().getId()));

        return Fiche360Response.builder()
                .identite(identite(medecin, actions, retours))
                .indicateurs(indicateurs(medecin, mois, tousDossiers, retours, actions, actionsVisitees, reclamations))
                .mois(mois)
                .evenements(evenements(mois, retours, actions, reclamations, alertes))
                .visites(retours.stream().map(Fiche360Service::toVisite).toList())
                .actions(actions.stream().map(a -> toAction(a, actionsVisitees)).toList())
                .reclamations(reclamations.stream().map(Fiche360Service::toReclamation).toList())
                .dossiers(dossiers)
                .obstacles(obstacles(retours))
                .commerciaux(commerciaux(retours))
                .dateEdition(aujourdhui)
                .build();
    }

    // ── Blocs ────────────────────────────────────────────────────────────────

    private static Identite identite(Medecin m, List<Action> actions, List<RetourTerrain> retours) {
        String referent = actions.stream().map(Action::getCommercial).filter(c -> !estVide(c)).findFirst()
                .orElse(retours.stream().map(RetourTerrain::getVisiteur).filter(c -> !estVide(c)).findFirst().orElse(null));
        return Identite.builder()
                .medecinId(m.getId())
                .codeMedecin(m.getCodeMedecin())
                .nomComplet(nomMedecin(m))
                .specialite(m.getSpecialite())
                .organisme(m.getOrganisme())
                .ville(m.getVille())
                .telephone(m.getTelephone())
                .email(m.getEmail())
                .segment(m.getSegment())
                .statut(m.getStatutPilotage() != null ? libelle(m.getStatutPilotage().name()) : libelle(m.getStatut()))
                .notePotentiel(m.getNoteInput())
                .commercialReferent(referent == null ? null : referent.trim())
                .build();
    }

    private static Indicateurs indicateurs(Medecin m, List<Mois> mois, List<ExtractionDonnees> dossiers,
                                           List<RetourTerrain> retours, List<Action> actions, Set<Long> actionsVisitees,
                                           List<Reclamation> reclamations) {
        long ca = mois.stream().mapToLong(Mois::getCa).sum();
        return Indicateurs.builder()
                .ca12Mois(ca)
                .dossiers12Mois(mois.stream().mapToLong(Mois::getDossiers).sum())
                .caMoyenMensuel(mois.isEmpty() ? 0 : Math.round(ca * 10.0 / mois.size()) / 10.0)
                .dernierDossier(dossiers.stream().map(ExtractionDonnees::getDateReception).filter(Objects::nonNull)
                        .max(Comparator.naturalOrder()).orElse(null))
                .joursSansActivite(m.getJoursSansActivite())
                .visites(retours.size())
                .visitesRealisees(retours.stream().filter(r -> r.getStatutVisite() != StatutVisite.NON_REALISEE).count())
                .derniereVisite(retours.stream().map(RetourTerrain::getDateVisite).filter(Objects::nonNull)
                        .max(Comparator.naturalOrder()).orElse(null))
                .actions(actions.size())
                .actionsOuvertes(actions.stream()
                        .filter(a -> a.getEtatAction() != EtatAction.REALISEE && !actionsVisitees.contains(a.getId())).count())
                .reclamations(reclamations.size())
                .reclamationsOuvertes(reclamations.stream().filter(Fiche360Service::estOuverte).count())
                .build();
    }

    static List<Evenement> evenements(List<Mois> mois, List<RetourTerrain> retours, List<Action> actions,
                                      List<Reclamation> reclamations, List<AlerteHebdomadaire> alertes) {
        List<Evenement> evenements = new ArrayList<>();
        for (RetourTerrain r : retours) {
            evenements.add(Evenement.builder()
                    .date(r.getDateVisite())
                    .type("VISITE")
                    .titre(titreVisite(r))
                    .detail(detailVisite(r))
                    .commercial(estVide(r.getVisiteur()) ? null : r.getVisiteur().trim())
                    .tonalite(tonaliteQualification(r))
                    .referenceId(r.getId())
                    .build());
        }
        for (Action a : actions) {
            YearMonth cycle = parseMois(a.getCycleMensuel());
            evenements.add(Evenement.builder()
                    .date(a.getDateEcheance() != null ? a.getDateEcheance() : cycle == null ? null : cycle.atDay(1))
                    .type("ACTION")
                    .titre("Action recommandée : " + libelle(a.getActionRecommandee()))
                    .detail("Cycle " + (cycle == null ? "—" : libelleMois(cycle))
                            + (a.getUrgence() == null ? "" : " · urgence " + libelle(a.getUrgence().name()).toLowerCase(Locale.FRENCH))
                            + " · " + (a.getEtatAction() == EtatAction.REALISEE ? "réalisée" : "planifiée"))
                    .commercial(estVide(a.getCommercial()) ? null : a.getCommercial().trim())
                    .tonalite(NEUTRE)
                    .referenceId(a.getId())
                    .build());
        }
        for (Reclamation r : reclamations) {
            evenements.add(Evenement.builder()
                    .date(r.getDateDeclaration())
                    .type("RECLAMATION")
                    .titre("Réclamation #" + r.getId() + " déclarée")
                    .detail(libelle(r.getCategorie() == null ? null : r.getCategorie().name())
                            + (estVide(r.getDescription()) ? "" : " · " + resume(r.getDescription())))
                    .commercial(estVide(r.getCommercialDeclarant()) ? null : r.getCommercialDeclarant().trim())
                    .tonalite(NEGATIF)
                    .referenceId(r.getId())
                    .build());
            if (r.getDateResolution() != null && !estOuverte(r)) {
                evenements.add(Evenement.builder()
                        .date(r.getDateResolution())
                        .type("RECLAMATION")
                        .titre("Réclamation #" + r.getId() + " " + libelle(r.getStatut().name()).toLowerCase(Locale.FRENCH))
                        .detail(estVide(r.getSolutionApportee()) ? null : resume(r.getSolutionApportee()))
                        .commercial(estVide(r.getResponsableTraitement()) ? null : r.getResponsableTraitement().trim())
                        .tonalite(POSITIF)
                        .referenceId(r.getId())
                        .build());
            }
        }
        for (int i = 1; i < mois.size(); i++) {
            String avant = mois.get(i - 1).getStatut();
            String apres = mois.get(i).getStatut();
            if (avant == null || apres == null || avant.equalsIgnoreCase(apres)) continue;
            String evolution = EvolutionStatutPolicy.evolution(avant, apres, true);
            evenements.add(Evenement.builder()
                    .date(YearMonth.parse(mois.get(i).getMois()).atDay(1))
                    .type("STATUT")
                    .titre("Statut : " + libelle(avant) + " → " + libelle(apres))
                    .detail("Changement observé en " + libelleMois(YearMonth.parse(mois.get(i).getMois())))
                    .tonalite(EvolutionStatutPolicy.FAVORABLE.equals(evolution) ? POSITIF
                            : EvolutionStatutPolicy.DEFAVORABLE.equals(evolution) ? NEGATIF : NEUTRE)
                    .build());
        }
        for (AlerteHebdomadaire a : alertes) {
            evenements.add(Evenement.builder()
                    .date(a.getDateDetection())
                    .type("ALERTE")
                    .titre("Alerte silence" + (a.getJoursSilence() == null ? "" : " : " + a.getJoursSilence() + " jours sans dossier"))
                    .detail(a.getStatutAlerte() == null ? null : "Statut de l'alerte : " + libelle(a.getStatutAlerte().name()).toLowerCase(Locale.FRENCH))
                    .tonalite(NEGATIF)
                    .referenceId(a.getId())
                    .build());
        }
        evenements.sort(Comparator.comparing(Evenement::getDate, Comparator.nullsLast(Comparator.reverseOrder())));
        return evenements;
    }

    static Map<String, Long> obstacles(List<RetourTerrain> retours) {
        Map<String, Long> comptes = retours.stream()
                .filter(r -> r.getObstaclePrincipal() != null)
                .collect(Collectors.groupingBy(r -> libelle(r.getObstaclePrincipal().name()), TreeMap::new, Collectors.counting()));
        return comptes.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    static List<CommercialLigne> commerciaux(List<RetourTerrain> retours) {
        Map<String, String> noms = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        retours.forEach(r -> {
            if (!estVide(r.getVisiteur())) noms.putIfAbsent(r.getVisiteur().trim(), r.getVisiteur().trim());
            if (!estVide(r.getCommercialAppui())) noms.putIfAbsent(r.getCommercialAppui().trim(), r.getCommercialAppui().trim());
        });
        Function<String, java.util.function.Predicate<String>> meme = nom -> c -> c != null && c.trim().equalsIgnoreCase(nom);
        return noms.values().stream().map(nom -> CommercialLigne.builder()
                        .commercial(nom)
                        .visites(retours.stream().filter(r -> meme.apply(nom).test(r.getVisiteur())).count())
                        .appuis(retours.stream().filter(r -> meme.apply(nom).test(r.getCommercialAppui())).count())
                        .derniereVisite(retours.stream()
                                .filter(r -> meme.apply(nom).test(r.getVisiteur()) || meme.apply(nom).test(r.getCommercialAppui()))
                                .map(RetourTerrain::getDateVisite).filter(Objects::nonNull)
                                .max(Comparator.naturalOrder()).orElse(null))
                        .build())
                .sorted(Comparator.comparingLong(CommercialLigne::getVisites).reversed())
                .toList();
    }

    // ── Conversions ──────────────────────────────────────────────────────────

    private static Visite toVisite(RetourTerrain r) {
        return Visite.builder()
                .id(r.getId())
                .date(r.getDateVisite())
                .commercial(r.getVisiteur())
                .commercialAppui(r.getCommercialAppui())
                .vactis(r.getAction() != null)
                .typeVisite(natureVisite(r))
                .statutVisite(r.getStatutVisite() == StatutVisite.NON_REALISEE ? "Non réalisée" : "Réalisée")
                .qualification(libelle(qualification(r).name()))
                .obstaclePrincipal(r.getObstaclePrincipal() == null ? null : libelle(r.getObstaclePrincipal().name()))
                .note(r.getNote())
                .commentaire(r.getCommentaire())
                .motifNonRealisation(libelleOuNull(r.getMotifNonRealisation()))
                .prochaineAction(libelleOuNull(r.getProchaineAction()))
                .dateProchaineAction(r.getDateProchaineAction())
                .reclamation(Boolean.TRUE.equals(r.getReclamation()) || qualification(r) == QualificationVisite.RECLAMATION)
                .build();
    }

    private static ActionLigne toAction(Action a, Set<Long> actionsVisitees) {
        return ActionLigne.builder()
                .id(a.getId())
                .cycle(a.getCycleMensuel())
                .actionRecommandee(libelle(a.getActionRecommandee()))
                .urgence(a.getUrgence() == null ? null : libelle(a.getUrgence().name()))
                .etat(a.getEtatAction() == EtatAction.REALISEE ? "Réalisée" : "Planifiée")
                .commercial(a.getCommercial())
                .dateEcheance(a.getDateEcheance())
                .visitee(a.getEtatAction() == EtatAction.REALISEE || actionsVisitees.contains(a.getId()))
                .build();
    }

    private static ReclamationLigne toReclamation(Reclamation r) {
        return ReclamationLigne.builder()
                .id(r.getId())
                .dateDeclaration(r.getDateDeclaration())
                .categorie(r.getCategorie() == null ? null : libelle(r.getCategorie().name()))
                .priorite(r.getPriorite() == null ? null : libelle(r.getPriorite().name()))
                .statut(r.getStatut() == null ? null : libelle(r.getStatut().name()))
                .ouverte(estOuverte(r))
                .commercial(r.getCommercialDeclarant())
                .responsable(r.getResponsableTraitement())
                .description(r.getDescription())
                .solution(r.getSolutionApportee())
                .dateResolution(r.getDateResolution())
                .build();
    }

    private static Dossier toDossier(ExtractionDonnees d) {
        return Dossier.builder()
                .dateReception(d.getDateReception())
                .reference(d.getReferenceDossier())
                .typeAnalyse(d.getTypeAnalyse())
                .nombreAnalyses(d.getNombreAnalyses())
                .statut(d.getStatutDossier() == null ? null : libelle(d.getStatutDossier().name()))
                .montant(d.getPrixAPayer())
                .urgent(d.isUrgent())
                .build();
    }

    // ── Libellés ─────────────────────────────────────────────────────────────

    private static String titreVisite(RetourTerrain r) {
        if (r.getStatutVisite() == StatutVisite.NON_REALISEE) return "Visite non réalisée";
        return natureVisite(r);
    }

    private static String natureVisite(RetourTerrain r) {
        if (r.getAction() != null) return "Visite VACTIS";
        if (r.getTypeVisite() == TypeVisite.SERVICE) return "Passage de service";
        return "Visite hors VACTIS";
    }

    private static String detailVisite(RetourTerrain r) {
        List<String> morceaux = new ArrayList<>();
        if (r.getStatutVisite() == StatutVisite.NON_REALISEE) {
            if (!estVide(r.getMotifNonRealisation())) morceaux.add("Motif : " + libelle(r.getMotifNonRealisation()).toLowerCase(Locale.FRENCH));
        } else if (!(r.getAction() == null && r.getTypeVisite() == TypeVisite.SERVICE)) {
            morceaux.add(libelle(qualification(r).name()));
        }
        if (r.getObstaclePrincipal() != null) morceaux.add("obstacle : " + libelle(r.getObstaclePrincipal().name()).toLowerCase(Locale.FRENCH));
        if (!estVide(r.getCommentaire())) morceaux.add("« " + resume(r.getCommentaire()) + " »");
        return morceaux.isEmpty() ? null : String.join(" · ", morceaux);
    }

    private static String tonaliteQualification(RetourTerrain r) {
        if (r.getStatutVisite() == StatutVisite.NON_REALISEE) return NEUTRE;
        return switch (qualification(r)) {
            case FAVORABLE -> POSITIF;
            case DEFAVORABLE, RECLAMATION -> NEGATIF;
            default -> NEUTRE;
        };
    }

    private static QualificationVisite qualification(RetourTerrain r) {
        return r.getQualification() == null ? QualificationVisite.NON_RENSEIGNE : r.getQualification();
    }

    private static boolean estOuverte(Reclamation r) {
        return r.getStatut() != null && RECLAMATIONS_OUVERTES.contains(r.getStatut());
    }

    private static final String[] MOIS = {"janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août",
            "sept.", "oct.", "nov.", "déc."};

    static String libelleMois(YearMonth ym) {
        return MOIS[ym.getMonthValue() - 1] + " " + ym.getYear();
    }

    private static YearMonth parseMois(String valeur) {
        try {
            return estVide(valeur) ? null : YearMonth.parse(valeur.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Libellés français des codes métier (énumérations et statuts) ; à défaut « ACTIF_STABLE » → « Actif stable ». */
    private static final Map<String, String> LIBELLES = Map.ofEntries(
            Map.entry("DELAI", "Délais"), Map.entry("PRIX_TARIF", "Prix / tarif"), Map.entry("QUALITE", "Qualité"),
            Map.entry("ORGANISATION_LOGISTIQUE", "Organisation / logistique"),
            Map.entry("DEFAVORABLE", "Défavorable"), Map.entry("RECLAMATION", "Réclamation"),
            Map.entry("NON_RENSEIGNE", "Non renseigné"), Map.entry("CONGE_ABSENCE", "Congé / absence"),
            Map.entry("A_TRAITER", "À traiter"), Map.entry("TRAITEE", "Traitée"),
            Map.entry("EN_ATTENTE_INFORMATION", "En attente d'information"),
            Map.entry("CLOTURE_SANS_ACTION", "Clôturée sans action"), Map.entry("CLOTUREE", "Clôturée"),
            Map.entry("RESOLUE", "Résolue"), Map.entry("REJETEE", "Rejetée"), Map.entry("IGNOREE", "Ignorée"),
            Map.entry("ACTION_CREEE", "Action créée"), Map.entry("RETENTION", "Rétention"),
            Map.entry("ACTIVITE_IRREGULIERE", "Activité irrégulière"), Map.entry("A_REACTIVER", "À réactiver"),
            Map.entry("ELEVE", "Élevée"), Map.entry("MOYEN", "Moyenne"), Map.entry("FAIBLE", "Faible"),
            Map.entry("DELAIS_RESULTATS", "Délais des résultats"), Map.entry("FACTURATION_TARIFS", "Facturation / tarifs"),
            Map.entry("LOGISTIQUE_COURSIER", "Logistique / coursier"), Map.entry("RELATIONNEL_ACCUEIL", "Relationnel / accueil"),
            Map.entry("MEDECIN_ABSENT", "Médecin absent"), Map.entry("VISITE_REPORTEE", "Visite reportée"),
            Map.entry("PRIORITE_MODIFIEE", "Priorité modifiée"), Map.entry("SECRETARIAT_BLOQUANT", "Secrétariat bloquant"),
            Map.entry("REPORT_DEMANDE", "Report demandé"), Map.entry("RECU", "Reçu"), Map.entry("TERMINE", "Terminé"),
            Map.entry("ANNULE", "Annulé"));

    public static String libelle(String code) {
        if (estVide(code)) return "—";
        String connu = LIBELLES.get(code.trim().toUpperCase(Locale.ROOT));
        if (connu != null) return connu;
        String texte = code.trim().replace('_', ' ').toLowerCase(Locale.FRENCH);
        return Character.toUpperCase(texte.charAt(0)) + texte.substring(1);
    }

    private static String libelleOuNull(String code) {
        return estVide(code) ? null : libelle(code);
    }

    private static String resume(String texte) {
        String propre = texte.trim().replaceAll("\\s+", " ");
        return propre.length() <= 140 ? propre : propre.substring(0, 137) + "…";
    }

    private static String nomMedecin(Medecin m) {
        String complet = ((m.getNom() == null ? "" : m.getNom()) + " " + (m.getPrenom() == null ? "" : m.getPrenom())).trim();
        return complet.isEmpty() ? "—" : complet;
    }

    private static boolean estVide(String s) {
        return s == null || s.isBlank();
    }
}
