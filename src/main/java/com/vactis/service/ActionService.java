package com.vactis.service;

import com.vactis.dto.action.ActionFilterOptionsResponse;
import com.vactis.dto.action.ActionKpiResponse;
import com.vactis.dto.action.ActionMetaResponse;
import com.vactis.dto.action.ActionPageResponse;
import com.vactis.model.action.Action;
import com.vactis.model.action.EtatAction;
import com.vactis.model.action.StatutPlanRetention;
import com.vactis.model.action.TypeEtapeRetention;
import com.vactis.model.action.UrgenceAction;
import com.vactis.model.medecin.RisqueUrgence;
import com.vactis.repository.ActionRepository;

import com.vactis.service.Activite.SegmentationService;
import com.vactis.service.RetourTerrainService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vactis.model.Controle.TypeControle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.vactis.dto.action.SaisieRetourTerrainRequest;
import com.vactis.dto.action.SaisieVisiteLibreRequest;
import com.vactis.dto.action.VisiteLibreResponse;
import com.vactis.dto.medecin.FicheContextuelleResponse;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.QualificationVisite;
import com.vactis.model.medecin.RetourTerrain;
import com.vactis.model.medecin.StatutVisite;
import com.vactis.model.medecin.TypeVisite;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.RetourTerrainRepository;
import com.vactis.repository.ExtractionDonneesRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;
import com.vactis.dto.reclamation.ReclamationRequest;
import com.vactis.model.reclamation.CategorieReclamation;
import com.vactis.model.reclamation.PrioriteReclamation;
import com.vactis.model.reclamation.Reclamation;
import com.vactis.model.reclamation.StatutReclamation;
import com.vactis.model.medecin.ObstaclePrincipal;

// Service métier pour la gestion, la recherche et le calcul des indicateurs des actions de pilotage
@Service
public class ActionService {
    private final ActionRepository actionRepository;
    private final ControleService controleService;
    private final MedecinService medecinService;
    private final RetourTerrainRepository retourTerrainRepository;
    private final MedecinRepository medecinRepository;
    private final SegmentationService segmentationService;
    private final ExtractionDonneesRepository extractionDonneesRepository;
    private final RetourTerrainService retourTerrainService;
    private final MoteurCommentaireService moteurCommentaireService;
    private final ReclamationService reclamationService;

    public ActionService(
            ActionRepository actionRepository,
            ControleService controleService,
            @Lazy MedecinService medecinService,
            RetourTerrainRepository retourTerrainRepository,
            MedecinRepository medecinRepository,
            SegmentationService segmentationService,
            ExtractionDonneesRepository extractionDonneesRepository,
            RetourTerrainService retourTerrainService,
            MoteurCommentaireService moteurCommentaireService,
            ReclamationService reclamationService
    ) {
        this.actionRepository = actionRepository;
        this.controleService = controleService;
        this.medecinService = medecinService;
        this.retourTerrainRepository = retourTerrainRepository;
        this.medecinRepository = medecinRepository;
        this.segmentationService = segmentationService;
        this.extractionDonneesRepository = extractionDonneesRepository;
        this.retourTerrainService = retourTerrainService;
        this.moteurCommentaireService = moteurCommentaireService;
        this.reclamationService = reclamationService;
    }

    // Retourne toutes les actions en base
    public List<Action> findAll() {
        return eligibleActions();
    }

    // Recherche une action par son identifiant
    public Action findById(Long id) {
        Action action = actionRepository.findById(id).orElse(null);
        return action == null || action.getMedecin() == null
            || medecinRepository.isPseudoMedecin(action.getMedecin()) ? null : action;
    }

    // Recherche les actions selon plusieurs critères de filtrage
    public List<Action> searchActions(
            String search,
            String statut,
            String segment,
            String action,
            UrgenceAction urgence,
            EtatAction etatAction,
            Boolean backlog,
            String commercial,
            String lieuOrganisme
    ) {
        return actionRepository.searchActions(
                normalize(search),
                normalize(statut),
                normalize(segment),
                normalize(action),
                urgence,
                etatAction,
                backlog,
                normalize(commercial),
                normalize(lieuOrganisme)
            ).stream().filter(a -> a.getMedecin() != null
                && !medecinRepository.isPseudoMedecin(a.getMedecin())).toList();
    }

    // Retourne les options de filtres distinctes (actions, commerciaux, lieux, statuts, segments)
    public ActionFilterOptionsResponse getFilterOptions() {
        ActionFilterOptionsResponse filters = new ActionFilterOptionsResponse();
        List<Action> actions = eligibleActions();
        filters.setActions(actions.stream().map(Action::getActionRecommandee).filter(Objects::nonNull).distinct().sorted().toList());
        filters.setCommerciaux(actions.stream().map(Action::getCommercial).filter(Objects::nonNull).distinct().sorted().toList());
        filters.setLieuxOrganismes(actions.stream().map(Action::getLieuOrganisme).filter(Objects::nonNull).distinct().sorted().toList());
        filters.setStatuts(controleService.getEtatsActifs(TypeControle.STATUT));
        filters.setSegments(controleService.getEtatsActifs(TypeControle.SEGEMENTS));
        return filters;
    }

    // Calcule les KPIs des actions (total, planifiées, réalisées, backlog, urgence silence)
    public ActionKpiResponse getKpis() {
        ActionKpiResponse kpis = new ActionKpiResponse();
        List<Action> actions = eligibleActions();
        kpis.setActionsGenerees((long) actions.size());
        kpis.setPlanifiees(actions.stream().filter(a -> a.getEtatAction() == EtatAction.PLANIFIEE).count());
        kpis.setVisites(actions.stream().filter(a -> a.getEtatAction() == EtatAction.REALISEE).count());
        kpis.setBacklog(actions.stream().filter(a -> Boolean.TRUE.equals(a.getBacklog())).count());
        kpis.setUrgenceSilence(actions.stream().filter(a -> Boolean.TRUE.equals(a.getUrgenceSilence())).count());
        return kpis;
    }

    // Compte les actions à l'état PLANIFIEE
    public Long countPlanifiees() {
        return eligibleActions().stream().filter(a -> a.getEtatAction() == EtatAction.PLANIFIEE).count();
    }

    // Construit la réponse complète de la page des actions (liste, KPIs, méta, filtres)
    public ActionPageResponse getActionPage(
            String search,
            String statut,
            String segment,
            String action,
            UrgenceAction urgence,
            EtatAction etatAction,
            Boolean backlog,
            String commercial,
            String lieuOrganisme
    ) {
        medecinService.recalculerStatutsEtSegmentsDynamiques();
        syncActionsWithMedecins();

        List<Action> items = searchActions(
                search,
                statut,
                segment,
                action,
                urgence,
                etatAction,
                backlog,
                commercial,
                lieuOrganisme
        );

            items.forEach(item -> item.setDerniereNoteTerrain(
                retourTerrainService.getDerniereVisite(item.getMedecin())
                    .map(RetourTerrain::getNote)
                    .orElse(null)
            ));
            items.forEach(item -> item.setJoursSansActivite(
                calculateJoursSansActivite(item.getMedecin())
            ));
            items.forEach(item -> {
                if (item.getMedecin() != null) {
                    MoteurCommentaireService.CommentaireMoteurDto synth = moteurCommentaireService.genererCommentaire(item.getMedecin());
                    item.setCommentaireMoteurResume(moteurCommentaireService.genererResumeCourt(item.getMedecin(), synth));
                    item.setCommentaireMoteurTitre(synth.titreSynthese());
                    item.setCommentaireMoteurDetail(moteurCommentaireService.genererCommentaireDetaille(synth));
                }
            });

        ActionMetaResponse meta = new ActionMetaResponse();
        meta.setAffiches((long) items.size());
        meta.setCharges((long) eligibleActions().size());

        ActionPageResponse response = new ActionPageResponse();
        response.setItems(items);
        response.setKpis(getKpis());
        response.setMeta(meta);
        response.setFilters(getFilterOptions());
        return response;
    }

    @Transactional
    public void syncActionsWithMedecins() {
        List<Action> actions = actionRepository.findAll();
        List<Action> actionsToDelete = new ArrayList<>();
        Set<Long> medecinsAvecAction = new HashSet<>();
        Set<String> importedActionKeys = new HashSet<>();
        Set<String> completedActionKeys = new HashSet<>();

        Set<Long> completedDoctorIds = new HashSet<>();

        for (Action action : actions) {
            if (action.getMedecin() == null || action.getEtatAction() != EtatAction.REALISEE
                    || medecinRepository.isPseudoMedecin(action.getMedecin())) {
                continue;
            }
            completedDoctorIds.add(action.getMedecin().getId());
            String currentStatus = action.getMedecin().getStatut() == null
                    ? "ACTIF_STABLE" : action.getMedecin().getStatut().toUpperCase();
            String expectedAction = libelleActionPourStatut(currentStatus);
            if (expectedAction != null
                    && canonicalActionLabel(expectedAction).equals(canonicalActionLabel(action.getActionRecommandee()))) {
                completedActionKeys.add(actionKey(action.getMedecin().getId(), expectedAction));
            }
        }

        boolean modifie = false;
        LocalDate baseDate = derniereReferencePourAction();

        for (Action a : actions) {
            if (a.getMedecin() != null) {
                Medecin m = a.getMedecin();
                if (medecinRepository.isPseudoMedecin(m)) continue;

                String statutMed = m.getStatut() != null ? m.getStatut().toUpperCase() : "ACTIF_STABLE";
                if (isUntouchedImportedAction(a)) {
                    String expectedAction = libelleActionPourStatut(statutMed);
                    String importedActionKey = actionKey(m.getId(), expectedAction == null
                            ? canonicalActionLabel(a.getActionRecommandee()) : expectedAction);
                    if (completedActionKeys.contains(importedActionKey) || completedDoctorIds.contains(m.getId())) {
                        actionsToDelete.add(a);
                        continue;
                    }
                    if (!importedActionKeys.add(importedActionKey) || !medecinsAvecAction.add(m.getId())) {
                        actionsToDelete.add(a);
                        continue;
                    }
                }

                // Section 1 : mapping statut -> libelle d'action conforme a la reference R.
                // INACTIF / ACTIVITE_IRREGULIERE / A_REACTIVER -> pas d'action automatique.
                // Les actions planifiées pour les inactifs doivent être supprimées (25 médecins - 3 inactifs = 22 actions).
                String libelleAttendu = libelleActionPourStatut(statutMed);
                if (libelleAttendu == null || "INACTIF".equals(statutMed) || "EXCLU".equals(statutMed)) {
                    if (a.getEtatAction() == EtatAction.PLANIFIEE) {
                        actionsToDelete.add(a);
                    }
                    continue;
                }

                medecinsAvecAction.add(m.getId());

                // Synchroniser systematiquement le statut et le segment
                if (!statutMed.equalsIgnoreCase(a.getStatut())) {
                    a.setStatut(statutMed);
                    modifie = true;
                }
                if (m.getSegment() != null && !m.getSegment().equalsIgnoreCase(a.getSegment())) {
                    a.setSegment(m.getSegment().toUpperCase());
                    modifie = true;
                }

                if (!libelleAttendu.equals(a.getActionRecommandee())) {
                    a.setActionRecommandee(libelleAttendu);
                    modifie = true;
                }

                // Urgence : FAIBLE pour Progression/Actif, ELEVE pour Surveillance/Retention/Onboarding
                UrgenceAction urgenceAttendue = urgencePourStatut(statutMed);
                if (urgenceAttendue != null && urgenceAttendue != a.getUrgence()) {
                    a.setUrgence(urgenceAttendue);
                    a.setUrgenceSilence(false);
                    modifie = true;
                }

                boolean segmentStrategique = List.of("A", "B").contains(
                        m.getSegment() == null ? "" : m.getSegment().toUpperCase());
                boolean silenceCritiqueStrategique = segmentStrategique
                        && Boolean.TRUE.equals(a.getUrgenceSilence());
                int horizon = ActionHorizonPolicy.recommanderHorizon(
                    m.getNiveauIntensite(), silenceCritiqueStrategique);
                boolean horizonModifie = !Objects.equals(a.getHorizonJours(), horizon);
                a.setHorizonJours(horizon);

                // Initialisation du plan de retention sequentiel (Section 3)
                if ("RETENTION".equals(statutMed)) {
                    if (a.getTypeEtapeRetention() == null || a.getTypeEtapeRetention() == TypeEtapeRetention.AUCUNE) {
                        a.setTypeEtapeRetention(TypeEtapeRetention.ACTION_1);
                        a.setStatutPlanRetention(StatutPlanRetention.EN_COURS);
                        modifie = true;
                    }
                }

                // Date de visite et echeance calculees a partir de la fin du dernier mois avec donnees
                // (et non depuis LocalDate.now()) pour rester coherent avec la dateReference du cycle.
                if (a.getEtatAction() == EtatAction.PLANIFIEE) {
                    if (horizonModifie || a.getDateVisite() == null || a.getDateVisite().isBefore(baseDate)) {
                        a.setDateVisite(baseDate.plusDays(horizon));
                        modifie = true;
                    }
                    LocalDate echeance = a.getDateVisite() != null ? a.getDateVisite() : baseDate.plusDays(horizon);
                    a.setDateEcheance(echeance);
                    boolean enRetard = echeance.isBefore(LocalDate.now());
                    if (!Boolean.valueOf(enRetard).equals(a.getEstEnRetard())) {
                        a.setEstEnRetard(enRetard);
                        modifie = true;
                    }
                } else {
                    a.setEstEnRetard(false);
                }
            }
        }

        if (!actionsToDelete.isEmpty()) {
            actionRepository.deleteAll(actionsToDelete);
            actions.removeAll(actionsToDelete);
        }

        // Créer les actions manquantes pour les médecins éligibles sans action
        String[] commerciaux = new String[]{"Karim Bennani", "Salma Idrissi"};
        int idx = actions.size();
        String cycleCourant = YearMonth.from(baseDate).toString();
        List<Action> nouvellesActions = new ArrayList<>();

        List<Medecin> medecinsEligibles = medecinRepository.findAllWithImportedDossiers().stream()
                .filter(m -> !medecinRepository.isPseudoMedecin(m))
                .toList();

        for (Medecin m : medecinsEligibles) {
            String statutMed = m.getStatut() != null ? m.getStatut().toUpperCase() : "ACTIF_STABLE";
            String libelle = libelleActionPourStatut(statutMed);
            if (libelle != null && !"INACTIF".equals(statutMed) && !"EXCLU".equals(statutMed)) {
                if (!medecinsAvecAction.contains(m.getId())) {
                    Action na = new Action();
                    na.setMedecin(m);
                    na.setStatut(statutMed);
                    na.setSegment(m.getSegment() != null ? m.getSegment().toUpperCase() : "C");
                    na.setActionRecommandee(libelle);
                    na.setUrgence(urgencePourStatut(statutMed));
                    na.setEtatAction(EtatAction.PLANIFIEE);
                    na.setCommercial(commerciaux[idx++ % commerciaux.length]);
                    na.setLieuOrganisme(m.getOrganisme() != null ? m.getOrganisme() : (m.getVille() != null ? m.getVille() : "Cabinet"));
                    na.setCycleMensuel(cycleCourant);
                    int horizon = ActionHorizonPolicy.recommanderHorizon(
                            m.getNiveauIntensite(), false);
                    na.setHorizonJours(horizon);
                    na.setDateVisite(baseDate.plusDays(horizon));
                    na.setDateEcheance(na.getDateVisite());
                    if ("RETENTION".equals(statutMed)) {
                        na.setTypeEtapeRetention(TypeEtapeRetention.ACTION_1);
                        na.setStatutPlanRetention(StatutPlanRetention.EN_COURS);
                    }
                    nouvellesActions.add(na);
                    actions.add(na);
                }
            }
        }
        if (!nouvellesActions.isEmpty()) {
            actionRepository.saveAll(nouvellesActions);
        }

        if (modifie) {
            actionRepository.saveAll(actions);
        }
    }

    /** Date de base pour le calcul des echeances d'actions : fin du dernier mois avec donnees,
     *  ou LocalDate.now() si la base est vide. Coherent avec SegmentationService.derniereReferenceData(). */
    private LocalDate derniereReferencePourAction() {
        List<LocalDate> dates = extractionDonneesRepository.findAllDatesDescending();
        return dates.isEmpty()
                ? LocalDate.now()
                : YearMonth.from(dates.get(0)).atEndOfMonth();
    }

    /**
     * Section 1 : libelle d'action conforme au document de regles (D) pour chaque statut.
     * Retourne null pour les statuts sans action automatique (INACTIF, ACTIVITE_IRREGULIERE, A_REACTIVER).
     *
     * Correspondance statut -> libelle D :
     *   PROGRESSION           -> visite suivi progression
     *   ACTIF_STABLE / ACTIF  -> visite suivi régulier
     *   SURVEILLANCE          -> visite diagnostic surveillance
     *   RETENTION             -> visite diagnostic
     *   ONBOARDING            -> visite onboarding
     */
    private static String libelleActionPourStatut(String statutMed) {
        return switch (statutMed) {
            case "PROGRESSION"                    -> "visite_reconnaissance";
            case "ACTIF_STABLE", "ACTIF"         -> "fidelisation";
            case "SURVEILLANCE"                   -> "visite_diagnostic_surveillance";
            case "RETENTION", "SILENCE_CRITIQUE" -> "visite_diagnostic";
            case "ONBOARDING"                     -> "visite_qualification";
            case "A_REACTIVER"                   -> "visite_prospection";
            default                               -> null;
        };
    }

    private static UrgenceAction urgencePourStatut(String statutMed) {
        return switch (statutMed) {
            case "PROGRESSION", "ACTIF_STABLE", "ACTIF" -> UrgenceAction.FAIBLE;
            case "SURVEILLANCE", "RETENTION", "SILENCE_CRITIQUE", "ONBOARDING" -> UrgenceAction.ELEVE;
            default -> null;
        };
    }


    // Permet à un commercial de se positionner et de réserver une action VACTIS
    @Transactional
    public Action reserverAction(Long idAction, String username) {
        Action action = actionRepository.findById(idAction)
                .orElseThrow(() -> new IllegalArgumentException("Action introuvable (ID: " + idAction + ")"));
        if (Boolean.TRUE.equals(action.getIsReserved())
                && action.getReservedBy() != null
                && !action.getReservedBy().equals(username)) {
            throw new IllegalStateException("Cette action est déjà réservée par " + action.getReservedBy() + ".");
        }
        action.setReservedBy(username);
        action.setReservedAt(LocalDateTime.now());
        action.setIsReserved(true);
        if (username != null && !username.isBlank()) {
            action.setCommercial(username);
        }
        return actionRepository.save(action);
    }

    // Valide et enregistre la saisie directe d'un retour terrain commercial pour une action VACTIS
    @Transactional
    public Action soumettreRetourTerrain(Long idAction, SaisieRetourTerrainRequest request, String username) {
        Action action = actionRepository.findById(idAction)
                .orElseThrow(() -> new IllegalArgumentException("Action introuvable (ID: " + idAction + ")"));

        validateVisitRequest(request.getActionRealisee(), request.getDateVisite(), request.getMotifNonRealisation(), request.getQualification(), request.getCommentaire(), request.getNoteTerrain(), request.getDateProchaineAction(), request.getObstaclePrincipal(), request.getDateDepart(), request.getDateRetourPrevue(), request.getDateRevue());
        boolean realisee = Boolean.TRUE.equals(request.getActionRealisee());
        if (!realisee && (request.getMotifNonRealisation() == null || request.getMotifNonRealisation().isBlank())) {
            throw new IllegalArgumentException("Le motif de non-réalisation est obligatoire si l'action n'est pas réalisée.");
        }
        if ("RECLAMATION".equalsIgnoreCase(request.getQualification()) && (request.getCommentaire() == null || request.getCommentaire().isBlank())) {
            throw new IllegalArgumentException("Le commentaire est obligatoire en cas de réclamation.");
        }

        action.setEtatAction(realisee ? EtatAction.REALISEE : EtatAction.PLANIFIEE);
        action.setMotifNonRealisation(request.getMotifNonRealisation());
        action.setQualification(request.getQualification());
        action.setObstaclePrincipal(parseObstacle(request.getObstaclePrincipal()));
        action.setDateDepart(request.getDateDepart());
        action.setDateRetourPrevue(request.getDateRetourPrevue());
        action.setDateRevue(request.getDateRevue());
        action.setCommentaire(request.getCommentaire());
        action.setProchaineAction(request.getProchaineAction());
        action.setDateProchaineAction(request.getDateProchaineAction());
        if (request.getDateVisite() != null) {
            action.setDateVisite(request.getDateVisite());
        }

        Medecin m = action.getMedecin();
        if (m != null) {
            RetourTerrain rt = new RetourTerrain();
            rt.setMedecin(m);
            rt.setAction(action);
            rt.setDateVisite(request.getDateVisite() != null ? request.getDateVisite() : LocalDate.now());
            rt.setStatutVisite(realisee ? StatutVisite.REALISEE : StatutVisite.NON_REALISEE);
            rt.setCommentaire(request.getCommentaire());
            rt.setMotifNonRealisation(request.getMotifNonRealisation());
            rt.setProchaineAction(request.getProchaineAction());
            rt.setDateProchaineAction(request.getDateProchaineAction());
            rt.setVisiteur(username != null ? username : action.getCommercial());
            rt.setCommercialAppui(commercialAppui(request.getCommercialAppui(), rt.getVisiteur()));
            rt.setNote(request.getNoteTerrain());
            rt.setQualification(parseQualification(request.getQualification()));
            rt.setDateDepart(request.getDateDepart());
            rt.setDateRetourPrevue(request.getDateRetourPrevue());
            rt.setDateRevue(request.getDateRevue());
            rt.setObstaclePrincipal(parseObstacle(request.getObstaclePrincipal()));
            if ("RECLAMATION".equalsIgnoreCase(request.getQualification())) {
                rt.setReclamation(true);
            }
            rt = retourTerrainRepository.save(rt);

            if ("RECLAMATION".equalsIgnoreCase(request.getQualification())) {
                Reclamation reclamation = creerTicketReclamationAutomatique(m, request.getCommentaire(),
                        rt.getObstaclePrincipal(), rt.getId(), rt.getDateVisite(),
                        username != null ? username : action.getCommercial());
                if (reclamation != null) action.setReclamationId(reclamation.getId());
            }
        }

        return actionRepository.save(action);
    }

    // Enregistre une visite commerciale libre (hors VACTIS)
    @Transactional
    public RetourTerrain creerVisiteLibre(SaisieVisiteLibreRequest request, String username) {
        boolean service = estPassageService(request.getTypePassage());
        // Un passage de service n'est pas un échange commercial : il n'est pas qualifié
        if (service) request.setQualification(QualificationVisite.NON_RENSEIGNE.name());
        validateVisitRequest(request.getActionRealisee(), request.getDateVisite(), request.getMotifNonRealisation(), request.getQualification(), request.getCommentaire(), request.getNoteTerrain(), request.getDateProchaineAction(), request.getObstaclePrincipal(), request.getDateDepart(), request.getDateRetourPrevue(), request.getDateRevue());
        Medecin medecin = null;
        if (request.getMedecinId() != null) {
            medecin = medecinRepository.findById(request.getMedecinId())
                    .orElseThrow(() -> new IllegalArgumentException("Médecin introuvable"));
        } else if (request.getNomMedecin() != null && !request.getNomMedecin().isBlank()) {
            medecin = new Medecin();
            medecin.setNom(request.getNomMedecin());
            medecin.setPrenom(request.getPrenomMedecin() != null ? request.getPrenomMedecin() : "");
            medecin.setSpecialite(request.getSpecialite() != null ? request.getSpecialite() : "Généraliste");
            medecin.setOrganisme(request.getOrganisme() != null ? request.getOrganisme() : "Cabinet privé");
            medecin.setCodeMedecin("MED_LIBRE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 9));
            medecin.setStatut("ONBOARDING");
            medecin.setSegment("D");
            medecin = medecinRepository.save(medecin);
        } else {
            throw new IllegalArgumentException("Veuillez sélectionner un médecin ou saisir le nom du nouveau médecin.");
        }

        RetourTerrain rt = new RetourTerrain();
        rt.setMedecin(medecin);
        rt.setAction(null);
        LocalDate dateVisite = request.getDateVisite() != null ? request.getDateVisite() : LocalDate.now();
        rt.setDateVisite(dateVisite);
        rt.setStatutVisite(Boolean.FALSE.equals(request.getActionRealisee()) ? StatutVisite.NON_REALISEE : StatutVisite.REALISEE);
        rt.setCommentaire(request.getCommentaire());
        rt.setMotifNonRealisation(request.getMotifNonRealisation());
        rt.setProchaineAction(request.getProchaineAction());
        rt.setDateProchaineAction(request.getDateProchaineAction());
        rt.setVisiteur(username != null ? username : "Commercial");
        rt.setCommercialAppui(commercialAppui(request.getCommercialAppui(), rt.getVisiteur()));
        if (service) rt.setTypeVisite(TypeVisite.SERVICE);
        rt.setNote(request.getNoteTerrain());
        rt.setQualification(parseQualification(request.getQualification()));
        rt.setDateDepart(request.getDateDepart());
        rt.setDateRetourPrevue(request.getDateRetourPrevue());
        rt.setDateRevue(request.getDateRevue());
        if (!service) {
            rt.setObstaclePrincipal(parseObstacle(request.getObstaclePrincipal()));
        }
        if ("RECLAMATION".equalsIgnoreCase(request.getQualification())) {
            rt.setReclamation(true);
        }

        rt = retourTerrainRepository.save(rt);

        if ("RECLAMATION".equalsIgnoreCase(request.getQualification())) {
            creerTicketReclamationAutomatique(medecin, request.getCommentaire(), rt.getObstaclePrincipal(), rt.getId(),
                    rt.getDateVisite(), username != null ? username : "Commercial");
        }

        return rt;
    }

    private Reclamation creerTicketReclamationAutomatique(Medecin m, String description, ObstaclePrincipal obstacle,
                                                          Long retourTerrainId, LocalDate dateVisite, String declarant) {
        ReclamationRequest recReq = new ReclamationRequest();
        recReq.setMedecinId(m.getId());
        recReq.setDescription(description != null && !description.isBlank() ? description : "Réclamation signalée sur le terrain");
        recReq.setPriorite(PrioriteReclamation.HAUTE);
        recReq.setRetourTerrainId(retourTerrainId);
        recReq.setDateVisite(dateVisite);
        if (obstacle != null) {
            switch (obstacle) {
                case QUALITE, DELAI, QUALITE_DELAI -> recReq.setCategorie(CategorieReclamation.DELAIS_RESULTATS);
                case PRIX_TARIF -> recReq.setCategorie(CategorieReclamation.FACTURATION_TARIFS);
                case RELATION, RELATIONNEL_ACCUEIL -> recReq.setCategorie(CategorieReclamation.RELATIONNEL_ACCUEIL);
                default -> recReq.setCategorie(CategorieReclamation.AUTRE);
            }
        } else {
            recReq.setCategorie(CategorieReclamation.AUTRE);
        }
        return reclamationService.creerReclamation(recReq, declarant);
    }

    void validateVisitRequest(Boolean actionRealisee, LocalDate dateVisite, String motif,
                                      String qualification, String commentaire, Double noteTerrain,
                                      LocalDate dateProchaineAction, String obstaclePrincipal,
                                      LocalDate dateDepart, LocalDate dateRetourPrevue, LocalDate dateRevue) {
        if (dateVisite == null || dateVisite.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La date réelle de visite est obligatoire et ne peut pas être future.");
        }
        if (Boolean.FALSE.equals(actionRealisee) && (motif == null || motif.isBlank())) {
            throw new IllegalArgumentException("Le motif de non-réalisation est obligatoire.");
        }
        if (Boolean.FALSE.equals(actionRealisee) && !List.of(
                "MEDECIN_ABSENT", "MANQUE_DE_TEMPS", "VISITE_REPORTEE", "PRIORITE_MODIFIEE",
                "ADRESSE_INCORRECTE_INDISPONIBLE", "AUTRE").contains(motif.trim().toUpperCase())) {
            throw new IllegalArgumentException("Motif de non-réalisation invalide.");
        }
        if (Boolean.TRUE.equals(actionRealisee) && (qualification == null || qualification.isBlank())) {
            throw new IllegalArgumentException("La qualification est obligatoire après une action réalisée.");
        }
        if ("CONGE_ABSENCE".equalsIgnoreCase(qualification)
                && (dateDepart == null
                || (dateRetourPrevue == null && dateRevue == null)
                || (dateRetourPrevue != null && dateRetourPrevue.isBefore(dateDepart))
                || (dateRevue != null && dateRevue.isBefore(dateDepart)))) {
            throw new IllegalArgumentException("Saisissez la date de départ et une date de retour prévue ou une date de revue cohérente.");
        }
        if ("RECLAMATION".equalsIgnoreCase(qualification) && (commentaire == null || commentaire.isBlank())) {
            throw new IllegalArgumentException("Le commentaire est obligatoire en cas de réclamation.");
        }
        if ("DEFAVORABLE".equalsIgnoreCase(qualification)) {
            if (commentaire == null || commentaire.isBlank()) {
                throw new IllegalArgumentException("Le commentaire est obligatoire en cas de qualification défavorable.");
            }
            if (obstaclePrincipal == null || !List.of("PRIX_TARIF", "QUALITE", "DELAI", "RELATION", "CONCURRENCE",
                    "ORGANISATION_LOGISTIQUE", "REFUS_EXPLICITE", "AUTRE").contains(obstaclePrincipal.trim().toUpperCase())) {
                throw new IllegalArgumentException("L'obstacle principal est obligatoire et doit être une valeur reconnue.");
            }
        }
        if (noteTerrain != null && (noteTerrain < 1.0 || noteTerrain > 5.0)) {
            throw new IllegalArgumentException("La note potentielle doit être comprise entre 1 et 5.");
        }
        if (dateProchaineAction != null && dateProchaineAction.isBefore(dateVisite)) {
            throw new IllegalArgumentException("La date de la prochaine action doit suivre la date de visite.");
        }
        parseQualification(qualification);
    }

    static boolean estPassageService(String typePassage) {
        if (typePassage == null || typePassage.isBlank() || "COMMERCIAL".equalsIgnoreCase(typePassage.trim())) return false;
        if ("SERVICE".equalsIgnoreCase(typePassage.trim())) return true;
        throw new IllegalArgumentException("Type de passage invalide (COMMERCIAL ou SERVICE).");
    }

    // L'appui est facultatif ; le visiteur principal ne peut pas être son propre appui
    static String commercialAppui(String appui, String visiteur) {
        if (appui == null || appui.isBlank()) return null;
        String valeur = appui.trim();
        if (visiteur != null && valeur.equalsIgnoreCase(visiteur.trim())) return null;
        return valeur.length() > 255 ? valeur.substring(0, 255) : valeur;
    }

    private QualificationVisite parseQualification(String qualification) {
        if (qualification == null || qualification.isBlank()) return QualificationVisite.NON_RENSEIGNE;
        try {
            return QualificationVisite.valueOf(qualification.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Qualification invalide.");
        }
    }

    private com.vactis.model.medecin.ObstaclePrincipal parseObstacle(String obstacle) {
        if (obstacle == null || obstacle.isBlank()) return null;
        try {
            return com.vactis.model.medecin.ObstaclePrincipal.valueOf(obstacle.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return com.vactis.model.medecin.ObstaclePrincipal.AUTRE;
        }
    }

    // Génère les données de la fiche contextuelle du médecin
    @Transactional
    public FicheContextuelleResponse getFicheContextuelle(Long medecinId) {
        return getFicheContextuelle(medecinId, true);
    }

    @Transactional
    public FicheContextuelleResponse getFicheContextuelle(Long medecinId, boolean isPilotage) {
        Medecin m = medecinRepository.findById(medecinId)
                .orElseThrow(() -> new IllegalArgumentException("Médecin introuvable"));

        List<RetourTerrain> historique = retourTerrainRepository.findByMedecinOrderByDateVisiteDescCreatedAtDesc(m);

        // Recherche de la date d'envoi du dernier dossier médical (patient au labo)
        Integer joursSansActivite = calculateJoursSansActivite(m);

        // Fréquence attendue basée sur le segment du médecin
        int frequenceJours = calculateFrequenceJours(m.getSegment());

        // Détermination du statut de silence radio
        String silenceRadioStatus;
        if ((joursSansActivite != null && joursSansActivite > frequenceJours)
                || (m.getStatut() != null && m.getStatut().toUpperCase().contains("SILENCE"))) {
            silenceRadioStatus = "SILENCE CRITIQUE";
        } else if (joursSansActivite != null && joursSansActivite > Math.round(frequenceJours * 0.7)) {
            silenceRadioStatus = "ALERTE SILENCE";
        } else if (joursSansActivite == null) {
            silenceRadioStatus = "DONNÉES INDISPONIBLES";
        } else {
            silenceRadioStatus = "SUIVI REGULIER";
        }

        // Synthèse narrative contextuelle issue du Moteur Commentaire (Section 8)
        MoteurCommentaireService.CommentaireMoteurDto synth = moteurCommentaireService.genererCommentaire(m);

        FicheContextuelleResponse resp = new FicheContextuelleResponse();
        resp.setMedecin(m);
        resp.setHistoriqueVisites(historique);
        resp.setStatutExplanation(synth.explicationDetaillee());
        resp.setCommentaireMoteurTitre(synth.titreSynthese());
        resp.setCommentaireMoteurAction(synth.actionConseillee());
        resp.setCommentaireMoteurResume(moteurCommentaireService.genererResumeCourt(m, synth));
        resp.setCommentaireMoteurDetail(moteurCommentaireService.genererCommentaireDetaille(synth));
        resp.setCommentaireMoteurDirection(isPilotage ? moteurCommentaireService.genererMessageDirection(m, synth) : null);
        resp.setSilenceRadioStatus(silenceRadioStatus);
        resp.setJoursSansActivite(joursSansActivite);
        resp.setFrequenceJours(frequenceJours);
        return resp;
    }

    private Integer calculateJoursSansActivite(Medecin medecin) {
        if (medecin.getJoursSansActivite() != null) {
            return medecin.getJoursSansActivite();
        }
        LocalDate lastDossierDate = extractionDonneesRepository.findMaxDateReceptionByMedecinId(medecin.getId());
        if (lastDossierDate == null) {
            lastDossierDate = medecin.getDateDerniereActivite();
        }

        if (lastDossierDate == null) {
            lastDossierDate = medecin.getDatePremiereCollaboration();
        }
        if (lastDossierDate == null) {
            return null;
        }

        LocalDate ref = derniereReferencePourAction();
        long diff = java.time.temporal.ChronoUnit.DAYS.between(lastDossierDate, ref);
        return diff > 0 ? (int) diff : 0;
    }

    private int calculateFrequenceJours(String segment) {
        if (segment == null) return 10;
        return switch (segment.trim().toUpperCase()) {
            case "A" -> 7;
            case "B" -> 10;
            case "C" -> 15;
            case "D" -> 30;
            default -> 10;
        };
    }

    // Retourne la liste de toutes les visites commerciales libres (hors VACTIS, action = null)
    // Mappe vers un DTO pour éviter la sérialisation des relations lazy
    @Transactional(readOnly = true)
    public List<VisiteLibreResponse> getVisitesLibres() {
        return retourTerrainRepository.findByActionIsNullOrderByDateVisiteDescCreatedAtDesc()
                .stream()
                .map(v -> {
                    VisiteLibreResponse dto = new VisiteLibreResponse();
                    dto.setId(v.getId());
                    dto.setDateVisite(v.getDateVisite());
                    dto.setVisiteur(v.getVisiteur());
                    dto.setQualification(v.getQualification() != null ? v.getQualification().name() : null);
                    dto.setCommentaire(v.getCommentaire());
                    dto.setTypeVisite(v.getTypeVisite() != null ? v.getTypeVisite().name() : null);
                    dto.setCommercialAppui(v.getCommercialAppui());
                    dto.setCreatedAt(v.getCreatedAt());
                    if (v.getMedecin() != null) {
                        Medecin m = v.getMedecin();
                        dto.setMedecinId(m.getId());
                        dto.setMedecinNom(m.getNom());
                        dto.setMedecinPrenom(m.getPrenom());
                        dto.setMedecinSpecialite(m.getSpecialite());
                        dto.setMedecinOrganisme(m.getOrganisme());
                        dto.setMedecinVille(m.getVille());
                    }
                    return dto;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    // Nettoie et normalise une chaîne (trim + null si vide)
    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<Action> eligibleActions() {
        return actionRepository.findAllForImportedDoctors().stream()
                .filter(a -> a.getMedecin() != null && !medecinRepository.isPseudoMedecin(a.getMedecin()))
                .toList();
    }

    private boolean isUntouchedImportedAction(Action action) {
        boolean importGenerated = "IMPORT_AUTO".equals(action.getSourceRecommandation());
        boolean legacyGenerated = action.getSourceRecommandation() == null
            && List.of("visite_reconnaissance", "fidelisation", "visite_diagnostic_surveillance",
                "visite_diagnostic", "visite_qualification", "visite_prospection",
                "visite suivi progression", "visite suivi régulier", "visite diagnostic surveillance",
                "visite diagnostic", "visite onboarding")
                .contains(action.getActionRecommandee());
        boolean isObsoleteRetentionAction2 = action.getTypeEtapeRetention() == TypeEtapeRetention.ACTION_2
            && action.getMedecin() != null
            && !"RETENTION".equalsIgnoreCase(action.getMedecin().getStatut());
        return (importGenerated || legacyGenerated || isObsoleteRetentionAction2)
                && action.getEtatAction() == EtatAction.PLANIFIEE
                && action.getDecisionRecommandation() == null
                && (action.getActionParenteId() == null || isObsoleteRetentionAction2)
                && (action.getTypeEtapeRetention() != TypeEtapeRetention.ACTION_2 || isObsoleteRetentionAction2)
                && !Boolean.TRUE.equals(action.getIsReserved())
                && action.getReclamationId() == null;
    }

    private static String canonicalActionLabel(String label) {
        return label == null ? "" : label.trim().toLowerCase().replace('_', ' ').replaceAll("\\s+", " ");
    }

    private static String actionKey(Long medecinId, String action) {
        return medecinId + "|" + canonicalActionLabel(action);
    }
}
