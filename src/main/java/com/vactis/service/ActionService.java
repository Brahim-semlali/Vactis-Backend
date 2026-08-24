package com.vactis.service;

import com.vactis.dto.action.ActionFilterOptionsResponse;
import com.vactis.dto.action.ActionKpiResponse;
import com.vactis.dto.action.ActionMetaResponse;
import com.vactis.dto.action.ActionPageResponse;
import com.vactis.model.action.Action;
import com.vactis.model.action.EtatAction;
import com.vactis.model.action.UrgenceAction;
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

    public ActionService(
            ActionRepository actionRepository,
            ControleService controleService,
            @Lazy MedecinService medecinService,
            RetourTerrainRepository retourTerrainRepository,
            MedecinRepository medecinRepository,
            SegmentationService segmentationService,
            ExtractionDonneesRepository extractionDonneesRepository,
            RetourTerrainService retourTerrainService
    ) {
        this.actionRepository = actionRepository;
        this.controleService = controleService;
        this.medecinService = medecinService;
        this.retourTerrainRepository = retourTerrainRepository;
        this.medecinRepository = medecinRepository;
        this.segmentationService = segmentationService;
        this.extractionDonneesRepository = extractionDonneesRepository;
        this.retourTerrainService = retourTerrainService;
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
                        || "SILENCE_CRITIQUE".equals(statutMed);
                if (silenceCritique || "SURVEILLANCE".equals(statutMed) || "RETENTION".equals(statutMed)) {
                    if (!"visite urgence silence".equals(a.getActionRecommandee())
                            || a.getUrgence() != UrgenceAction.SILENCE_CRITIQUE
                            || !Boolean.TRUE.equals(a.getUrgenceSilence())) {
                        a.setActionRecommandee("visite urgence silence");
                        a.setUrgence(UrgenceAction.SILENCE_CRITIQUE);
                        a.setUrgenceSilence(true);
                        a.setCommentaire("Relance prioritaire suite à une baisse d'activité ou à un silence radio prolongé.");
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

                // Ajuster la date de visite si elle est dans le passé pour les actions PLANIFIEE
                if (a.getEtatAction() == EtatAction.PLANIFIEE && (a.getDateVisite() == null || a.getDateVisite().isBefore(LocalDate.now()))) {
                    a.setDateVisite(LocalDate.now().plusDays(5));
                    modifie = true;
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

        validateVisitRequest(request.getActionRealisee(), request.getDateVisite(), request.getMotifNonRealisation(), request.getQualification(), request.getCommentaire(), request.getNoteTerrain(), request.getDateProchaineAction());
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
            if ("RECLAMATION".equalsIgnoreCase(request.getQualification())) {
                rt.setReclamation(true);
            }
            retourTerrainRepository.save(rt);
        }

        return actionRepository.save(action);
    }

    // Enregistre une visite commerciale libre (hors VACTIS)
    @Transactional
    public RetourTerrain creerVisiteLibre(SaisieVisiteLibreRequest request, String username) {
        validateVisitRequest(request.getActionRealisee(), request.getDateVisite(), request.getMotifNonRealisation(), request.getQualification(), request.getCommentaire(), request.getNoteTerrain(), request.getDateProchaineAction());
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
        if ("RECLAMATION".equalsIgnoreCase(request.getQualification())) {
            rt.setReclamation(true);
        }

        return retourTerrainRepository.save(rt);
    }

    private void validateVisitRequest(Boolean actionRealisee, LocalDate dateVisite, String motif,
                                      String qualification, String commentaire, Double noteTerrain,
                                      LocalDate dateProchaineAction) {
        if (dateVisite == null || dateVisite.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La date réelle de visite est obligatoire et ne peut pas être future.");
        }
        if (Boolean.FALSE.equals(actionRealisee) && (motif == null || motif.isBlank())) {
            throw new IllegalArgumentException("Le motif de non-réalisation est obligatoire.");
        }
        if ("RECLAMATION".equalsIgnoreCase(qualification) && (commentaire == null || commentaire.isBlank())) {
            throw new IllegalArgumentException("Le commentaire est obligatoire en cas de réclamation.");
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

        String statutUpper = m.getStatut() != null ? m.getStatut().toUpperCase() : "ACTIF_STABLE";
        String explanationText = switch (statutUpper) {
            case "PROGRESSION" -> "Statut calculé d'après une hausse significative de CA (> +20%) : PROGRESSION";
            case "ACTIF_STABLE" -> "Statut calculé d'après la stabilité du CA (-10% à +20%) : ACTIF_STABLE";
            case "SURVEILLANCE" -> "Statut calculé d'après une baisse modérée du CA (-10% à -40%) : SURVEILLANCE";
            case "RETENTION" -> "Statut calculé d'après une baisse sévère du CA (-40% à -70%) : RETENTION";
            case "SILENCE_CRITIQUE" -> "Statut calculé d'après une chute critique de CA ou un silence radio prolongé : SILENCE_CRITIQUE";
            case "ONBOARDING" -> "Médecin nouvellement intégré au portefeuille : ONBOARDING";
            default -> "Statut calculé d'après la variation de CA : " + m.getStatut();
        };

        FicheContextuelleResponse resp = new FicheContextuelleResponse();
        resp.setMedecin(m);
        resp.setHistoriqueVisites(historique);
        resp.setStatutExplanation(explanationText);
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
