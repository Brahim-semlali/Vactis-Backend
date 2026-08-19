package com.vactis.service;

import com.vactis.dto.action.ActionFilterOptionsResponse;
import com.vactis.dto.action.ActionKpiResponse;
import com.vactis.dto.action.ActionMetaResponse;
import com.vactis.dto.action.ActionPageResponse;
import com.vactis.model.action.Action;
import com.vactis.model.action.EtatAction;
import com.vactis.model.action.UrgenceAction;
import com.vactis.model.medecin.StatutPilotage;
import com.vactis.repository.ActionRepository;

import lombok.RequiredArgsConstructor;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

// Service métier pour la gestion, la recherche et le calcul des indicateurs des actions de pilotage
@Service
public class ActionService {
    private final ActionRepository actionRepository;
    private final ControleService controleService;
    private final MedecinService medecinService;
    private final RetourTerrainRepository retourTerrainRepository;
    private final MedecinRepository medecinRepository;
    private final SegmentationService segmentationService;

    public ActionService(
            ActionRepository actionRepository,
            ControleService controleService,
            @Lazy MedecinService medecinService,
            RetourTerrainRepository retourTerrainRepository,
            MedecinRepository medecinRepository,
            SegmentationService segmentationService
    ) {
        this.actionRepository = actionRepository;
        this.controleService = controleService;
        this.medecinService = medecinService;
        this.retourTerrainRepository = retourTerrainRepository;
        this.medecinRepository = medecinRepository;
        this.segmentationService = segmentationService;
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
                String statutMed = a.getMedecin().getStatut();
                if (statutMed != null && !statutMed.equalsIgnoreCase(a.getStatut())) {
                    String upperStatut = statutMed.toUpperCase();
                    a.setStatut(upperStatut);
                    
                    if ("SURVEILLANCE".equals(upperStatut) || "SILENCE_CRITIQUE".equals(upperStatut) || "RETENTION".equals(upperStatut)) {
                        a.setActionRecommandee("visite urgence silence");
                        a.setUrgence(UrgenceAction.SILENCE_CRITIQUE);
                        a.setUrgenceSilence(true);
                    } else if ("PROGRESSION".equals(upperStatut)) {
                        a.setActionRecommandee("visite suivi progression");
                        a.setUrgence(UrgenceAction.FAIBLE);
                        a.setUrgenceSilence(false);
                    } else if ("ONBOARDING".equals(upperStatut)) {
                        a.setActionRecommandee("visite onboarding");
                        a.setUrgence(UrgenceAction.ELEVE);
                        a.setUrgenceSilence(false);
                    }
                    modifie = true;
                }
                if (a.getMedecin().getSegment() != null && !a.getMedecin().getSegment().equalsIgnoreCase(a.getSegment())) {
                    a.setSegment(a.getMedecin().getSegment().toUpperCase());
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
            if (request.getNotePotentielle() != null) {
                m.setNoteInput(request.getNotePotentielle());
                medecinRepository.save(m);
                segmentationService.recalculerSegmentationPortefeuille();
            }

            RetourTerrain rt = new RetourTerrain();
            rt.setMedecin(m);
            rt.setAction(action);
            rt.setDateVisite(request.getDateVisite() != null ? request.getDateVisite() : LocalDate.now());
            rt.setStatutVisite(realisee ? StatutVisite.REALISEE : StatutVisite.NON_REALISEE);
            rt.setCommentaire(request.getCommentaire());
            rt.setVisiteur(username != null ? username : action.getCommercial());
            rt.setNote(request.getNotePotentielle() != null ? request.getNotePotentielle() : 3.0);
            try {
                if (request.getQualification() != null) {
                    rt.setQualification(QualificationVisite.valueOf(request.getQualification().toUpperCase()));
                }
            } catch (Exception ignored) {}
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
            medecin.setCodeMedecin("MED_LIBRE_" + (System.currentTimeMillis() % 10000));
            medecin.setStatut("ONBOARDING");
            medecin.setSegment("D");
            if (request.getNotePotentielle() != null) {
                medecin.setNoteInput(request.getNotePotentielle());
            }
            medecin = medecinRepository.save(medecin);
        } else {
            throw new IllegalArgumentException("Veuillez sélectionner un médecin ou saisir le nom du nouveau médecin.");
        }

        if (request.getNotePotentielle() != null && request.getMedecinId() != null) {
            medecin.setNoteInput(request.getNotePotentielle());
            medecinRepository.save(medecin);
            segmentationService.recalculerSegmentationPortefeuille();
        }

        RetourTerrain rt = new RetourTerrain();
        rt.setMedecin(medecin);
        rt.setAction(null);
        rt.setDateVisite(request.getDateVisite() != null ? request.getDateVisite() : LocalDate.now());
        rt.setStatutVisite(Boolean.FALSE.equals(request.getActionRealisee()) ? StatutVisite.NON_REALISEE : StatutVisite.REALISEE);
        rt.setCommentaire(request.getCommentaire());
        rt.setVisiteur(username != null ? username : "Commercial");
        rt.setNote(request.getNotePotentielle() != null ? request.getNotePotentielle() : 3.0);
        try {
            if (request.getQualification() != null) {
                rt.setQualification(QualificationVisite.valueOf(request.getQualification().toUpperCase()));
            }
        } catch (Exception ignored) {}
        if ("RECLAMATION".equalsIgnoreCase(request.getQualification())) {
            rt.setReclamation(true);
        }
        return retourTerrainRepository.save(rt);
    }

    // Génère les données de la fiche contextuelle du médecin
    public FicheContextuelleResponse getFicheContextuelle(Long medecinId) {
        Medecin m = medecinRepository.findById(medecinId)
                .orElseThrow(() -> new IllegalArgumentException("Médecin introuvable"));

        List<RetourTerrain> historique = retourTerrainRepository.findByMedecinOrderByDateVisiteDescCreatedAtDesc(m);

        FicheContextuelleResponse resp = new FicheContextuelleResponse();
        resp.setMedecin(m);
        resp.setHistoriqueVisites(historique);
        resp.setStatutExplanation(m.getStatut() != null ? "Statut calculé d'après la variation de CA : " + m.getStatut() : "Statut actif stable");
        resp.setSilenceRadioStatus(m.getStatut() != null && m.getStatut().contains("SILENCE") ? "SILENCE CRITIQUE" : "SUIVI REGULIER");
        resp.setJoursSansActivite(44);
        resp.setFrequenceJours(10);
        return resp;
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
