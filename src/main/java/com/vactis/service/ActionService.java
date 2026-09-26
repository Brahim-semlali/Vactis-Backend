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
import java.util.List;

import com.vactis.dto.action.SaisieRetourTerrainRequest;
import com.vactis.dto.action.SaisieVisiteLibreRequest;
import com.vactis.dto.action.VisiteLibreResponse;
import com.vactis.dto.medecin.FicheContextuelleResponse;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.QualificationVisite;
import com.vactis.model.medecin.RetourTerrain;
import com.vactis.model.medecin.StatutVisite;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.RetourTerrainRepository;
import com.vactis.repository.ExtractionDonneesRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import com.vactis.dto.reclamation.ReclamationRequest;
import com.vactis.model.reclamation.CategorieReclamation;
import com.vactis.model.reclamation.PrioriteReclamation;
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
        return actionRepository.findAll();
    }

    // Recherche une action par son identifiant
    public Action findById(Long id) {
        return actionRepository.findById(id).orElse(null);
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
        );
    }

    // Retourne les options de filtres distinctes (actions, commerciaux, lieux, statuts, segments)
    public ActionFilterOptionsResponse getFilterOptions() {
        ActionFilterOptionsResponse filters = new ActionFilterOptionsResponse();
        filters.setActions(actionRepository.findDistinctActions());
        filters.setCommerciaux(actionRepository.findDistinctCommerciaux());
        filters.setLieuxOrganismes(actionRepository.findDistinctLieuxOrganismes());
        filters.setStatuts(controleService.getEtatsActifs(TypeControle.STATUT));
        filters.setSegments(controleService.getEtatsActifs(TypeControle.SEGEMENTS));
        return filters;
    }

    // Calcule les KPIs des actions (total, planifiées, réalisées, backlog, urgence silence)
    public ActionKpiResponse getKpis() {
        ActionKpiResponse kpis = new ActionKpiResponse();
        kpis.setActionsGenerees(actionRepository.countAllActions());
        kpis.setPlanifiees(actionRepository.countByEtatAction(EtatAction.PLANIFIEE));
        kpis.setVisites(actionRepository.countByEtatAction(EtatAction.REALISEE));
        kpis.setBacklog(actionRepository.countByBacklogTrue());
        kpis.setUrgenceSilence(actionRepository.countByUrgenceSilenceTrue());
        return kpis;
    }

    // Compte les actions à l'état PLANIFIEE
    public Long countPlanifiees() {
        return actionRepository.countByEtatAction(EtatAction.PLANIFIEE);
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

        ActionMetaResponse meta = new ActionMetaResponse();
        meta.setAffiches((long) items.size());
        meta.setCharges(actionRepository.countAllActions());

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
        boolean modifie = false;
        for (Action a : actions) {
            if (a.getMedecin() != null) {
                Medecin m = a.getMedecin();
                String statutMed = m.getStatut() != null ? m.getStatut().toUpperCase() : "ACTIF_STABLE";
                
                // Synchroniser systématiquement le statut et le segment
                if (!statutMed.equalsIgnoreCase(a.getStatut())) {
                    a.setStatut(statutMed);
                    modifie = true;
                }
                if (m.getSegment() != null && !m.getSegment().equalsIgnoreCase(a.getSegment())) {
                    a.setSegment(m.getSegment().toUpperCase());
                    modifie = true;
                }

                // Ajuster l'action recommandée et l'urgence pour correspondre au statut du médecin
                int joursSansActivite = calculateJoursSansActivite(m);
                boolean silenceCritique = joursSansActivite > calculateFrequenceJours(m.getSegment())
                    || "SILENCE_CRITIQUE".equals(statutMed)
                    || m.getRisqueUrgence() == RisqueUrgence.URGENT;
                if (silenceCritique) {
                    if (!"visite urgence silence".equals(a.getActionRecommandee())
                            || a.getUrgence() != UrgenceAction.SILENCE_CRITIQUE
                            || !Boolean.TRUE.equals(a.getUrgenceSilence())) {
                        a.setActionRecommandee("visite urgence silence");
                        a.setUrgence(UrgenceAction.SILENCE_CRITIQUE);
                        a.setUrgenceSilence(true);
                        a.setCommentaire("Relance prioritaire suite à une baisse d'activité ou à un silence radio prolongé.");
                        modifie = true;
                    }
                } else if (m.getRisqueUrgence() == RisqueUrgence.ELEVE || "RETENTION".equals(statutMed)) {
                    if (!"visite urgence risque".equals(a.getActionRecommandee())) {
                        a.setActionRecommandee("visite urgence risque");
                        a.setUrgence(UrgenceAction.ELEVE);
                        a.setUrgenceSilence(false);
                        a.setCommentaire("Visite prioritaire selon le risque commercial calculé.");
                        modifie = true;
                    }
                } else if ("PROGRESSION".equals(statutMed)) {
                    if (!"visite suivi progression".equals(a.getActionRecommandee())) {
                        a.setActionRecommandee("visite suivi progression");
                        a.setUrgence(UrgenceAction.FAIBLE);
                        a.setUrgenceSilence(false);
                        a.setCommentaire("Visite de suivi progression effectuée.");
                        modifie = true;
                    }
                } else if ("ONBOARDING".equals(statutMed)) {
                    if (!"visite onboarding".equals(a.getActionRecommandee())) {
                        a.setActionRecommandee("visite onboarding");
                        a.setUrgence(UrgenceAction.ELEVE);
                        a.setUrgenceSilence(false);
                        a.setCommentaire("Première visite d'accompagnement.");
                        modifie = true;
                    }
                } else { // ACTIF_STABLE ou autre
                    if (!"visite suivi régulier".equals(a.getActionRecommandee())) {
                        a.setActionRecommandee("visite suivi régulier");
                        a.setUrgence(UrgenceAction.FAIBLE);
                        a.setUrgenceSilence(false);
                        a.setCommentaire("Visite de suivi commercial régulier et fidélisation.");
                        modifie = true;
                    }
                }

                // Horizons recommandés (Section 9) : critique stratégique J+3, urgence J+7,
                // intensité élevée J+15, intensité normale J+30.
                int horizon;
                if (Boolean.TRUE.equals(a.getUrgenceSilence()) || a.getUrgence() == UrgenceAction.SILENCE_CRITIQUE) {
                    horizon = 3;
                } else if (a.getUrgence() == UrgenceAction.URGENT) {
                    horizon = 7;
                } else if (a.getUrgence() == UrgenceAction.ELEVE || "RETENTION".equals(statutMed)) {
                    horizon = 15;
                } else if (a.getUrgence() == UrgenceAction.MOYEN || "SURVEILLANCE".equals(statutMed)) {
                    horizon = 30;
                } else {
                    horizon = 30;
                }
                a.setHorizonJours(horizon);

                // Initialisation du plan de rétention séquentiel (Section 3)
                if ("RETENTION".equals(statutMed)) {
                    if (a.getTypeEtapeRetention() == null || a.getTypeEtapeRetention() == TypeEtapeRetention.AUCUNE) {
                        a.setTypeEtapeRetention(TypeEtapeRetention.ACTION_1);
                        a.setStatutPlanRetention(StatutPlanRetention.EN_COURS);
                        modifie = true;
                    }
                }

                // Ajuster la date de visite et date d'échéance si nécessaire
                if (a.getEtatAction() == EtatAction.PLANIFIEE) {
                    if (a.getDateVisite() == null || a.getDateVisite().isBefore(LocalDate.now())) {
                        a.setDateVisite(LocalDate.now().plusDays(horizon));
                        modifie = true;
                    }
                    LocalDate echeance = a.getDateVisite() != null ? a.getDateVisite() : LocalDate.now().plusDays(horizon);
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
        if (modifie) {
            actionRepository.saveAll(actions);
        }
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

        validateVisitRequest(request.getActionRealisee(), request.getDateVisite(), request.getMotifNonRealisation(), request.getQualification(), request.getCommentaire(), request.getNoteTerrain(), request.getDateProchaineAction(), request.getObstaclePrincipal(), request.getDateDepart(), request.getDateRetourPrevue());
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
            rt.setNote(request.getNoteTerrain());
            rt.setQualification(parseQualification(request.getQualification()));
            rt.setDateDepart(request.getDateDepart());
            rt.setDateRetourPrevue(request.getDateRetourPrevue());
            rt.setObstaclePrincipal(parseObstacle(request.getObstaclePrincipal()));
            if ("RECLAMATION".equalsIgnoreCase(request.getQualification())) {
                rt.setReclamation(true);
            }
            rt = retourTerrainRepository.save(rt);

            if ("RECLAMATION".equalsIgnoreCase(request.getQualification())) {
                creerTicketReclamationAutomatique(m, request.getCommentaire(), rt.getObstaclePrincipal(), rt.getId(), username != null ? username : action.getCommercial());
            }
        }

        return actionRepository.save(action);
    }

    // Enregistre une visite commerciale libre (hors VACTIS)
    @Transactional
    public RetourTerrain creerVisiteLibre(SaisieVisiteLibreRequest request, String username) {
        validateVisitRequest(request.getActionRealisee(), request.getDateVisite(), request.getMotifNonRealisation(), request.getQualification(), request.getCommentaire(), request.getNoteTerrain(), request.getDateProchaineAction(), null, request.getDateDepart(), request.getDateRetourPrevue());
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
        rt.setNote(request.getNoteTerrain());
        rt.setQualification(parseQualification(request.getQualification()));
            rt.setDateDepart(request.getDateDepart());
            rt.setDateRetourPrevue(request.getDateRetourPrevue());
        if ("RECLAMATION".equalsIgnoreCase(request.getQualification())) {
            rt.setReclamation(true);
        }

        rt = retourTerrainRepository.save(rt);

        if ("RECLAMATION".equalsIgnoreCase(request.getQualification())) {
            creerTicketReclamationAutomatique(medecin, request.getCommentaire(), null, rt.getId(), username != null ? username : "Commercial");
        }

        return rt;
    }

    private void creerTicketReclamationAutomatique(Medecin m, String description, ObstaclePrincipal obstacle, Long retourTerrainId, String declarant) {
        try {
            ReclamationRequest recReq = new ReclamationRequest();
            recReq.setMedecinId(m.getId());
            recReq.setDescription(description != null && !description.isBlank() ? description : "Réclamation signalée sur le terrain");
            recReq.setPriorite(PrioriteReclamation.HAUTE);
            recReq.setRetourTerrainId(retourTerrainId);
            if (obstacle != null) {
                switch (obstacle) {
                    case QUALITE_DELAI -> recReq.setCategorie(CategorieReclamation.DELAIS_RESULTATS);
                    case PRIX_TARIF -> recReq.setCategorie(CategorieReclamation.FACTURATION_TARIFS);
                    case RELATIONNEL_ACCUEIL -> recReq.setCategorie(CategorieReclamation.RELATIONNEL_ACCUEIL);
                    default -> recReq.setCategorie(CategorieReclamation.AUTRE);
                }
            } else {
                recReq.setCategorie(CategorieReclamation.AUTRE);
            }
            reclamationService.creerReclamation(recReq, declarant);
        } catch (Exception e) {
            // Log but do not block the return submission if ticket fails
            org.slf4j.LoggerFactory.getLogger(ActionService.class).error("Erreur lors de la création auto du ticket réclamation", e);
        }
    }

    private void validateVisitRequest(Boolean actionRealisee, LocalDate dateVisite, String motif,
                                      String qualification, String commentaire, Double noteTerrain,
                                      LocalDate dateProchaineAction, String obstaclePrincipal,
                                      LocalDate dateDepart, LocalDate dateRetourPrevue) {
        if (dateVisite == null || dateVisite.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La date réelle de visite est obligatoire et ne peut pas être future.");
        }
        if (Boolean.FALSE.equals(actionRealisee) && (motif == null || motif.isBlank())) {
            throw new IllegalArgumentException("Le motif de non-réalisation est obligatoire.");
        }
        if (Boolean.TRUE.equals(actionRealisee) && (qualification == null || qualification.isBlank())) {
            throw new IllegalArgumentException("La qualification est obligatoire après une action réalisée.");
        }
        if ("CONGE_ABSENCE".equalsIgnoreCase(qualification)
                && (dateDepart == null || (dateRetourPrevue != null && dateRetourPrevue.isBefore(dateDepart)))) {
            throw new IllegalArgumentException("Les dates de départ et de retour prévue sont obligatoires et cohérentes pour un congé ou une absence.");
        }
        if ("RECLAMATION".equalsIgnoreCase(qualification) && (commentaire == null || commentaire.isBlank())) {
            throw new IllegalArgumentException("Le commentaire est obligatoire en cas de réclamation.");
        }
        if ("DEFAVORABLE".equalsIgnoreCase(qualification) && (obstaclePrincipal == null || obstaclePrincipal.isBlank())) {
            throw new IllegalArgumentException("L'obstacle principal est obligatoire en cas de qualification défavorable.");
        }
        if (noteTerrain != null && (noteTerrain < 1.0 || noteTerrain > 5.0)) {
            throw new IllegalArgumentException("La note potentielle doit être comprise entre 1 et 5.");
        }
        if (dateProchaineAction != null && dateProchaineAction.isBefore(dateVisite)) {
            throw new IllegalArgumentException("La date de la prochaine action doit suivre la date de visite.");
        }
        parseQualification(qualification);
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
        Medecin m = medecinRepository.findById(medecinId)
                .orElseThrow(() -> new IllegalArgumentException("Médecin introuvable"));

        List<RetourTerrain> historique = retourTerrainRepository.findByMedecinOrderByDateVisiteDescCreatedAtDesc(m);

        // Recherche de la date d'envoi du dernier dossier médical (patient au labo)
        int joursSansActivite = calculateJoursSansActivite(m);

        // Fréquence attendue basée sur le segment du médecin
        int frequenceJours = calculateFrequenceJours(m.getSegment());

        // Détermination du statut de silence radio
        String silenceRadioStatus;
        if (joursSansActivite > frequenceJours || (m.getStatut() != null && m.getStatut().toUpperCase().contains("SILENCE"))) {
            silenceRadioStatus = "SILENCE CRITIQUE";
        } else if (joursSansActivite > Math.round(frequenceJours * 0.7)) {
            silenceRadioStatus = "ALERTE SILENCE";
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
        resp.setSilenceRadioStatus(silenceRadioStatus);
        resp.setJoursSansActivite(joursSansActivite);
        resp.setFrequenceJours(frequenceJours);
        return resp;
    }

    private int calculateJoursSansActivite(Medecin medecin) {
        LocalDate lastDossierDate = extractionDonneesRepository.findMaxDateReceptionByMedecinId(medecin.getId());
        if (lastDossierDate == null) {
            lastDossierDate = medecin.getDateDerniereActivite();
        }

        if (lastDossierDate == null) {
            lastDossierDate = medecin.getDatePremiereCollaboration();
        }
        if (lastDossierDate == null) {
            return 0;
        }

        long diff = java.time.temporal.ChronoUnit.DAYS.between(lastDossierDate, LocalDate.now());
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
}
