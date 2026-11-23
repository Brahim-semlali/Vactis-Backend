package com.vactis.service;

import com.vactis.dto.reclamation.ReclamationRequest;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.reclamation.CategorieReclamation;
import com.vactis.model.reclamation.PrioriteReclamation;
import com.vactis.model.reclamation.Reclamation;
import com.vactis.model.reclamation.ReclamationHistorique;
import com.vactis.model.reclamation.StatutReclamation;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.ReclamationRepository;
import com.vactis.repository.ReclamationHistoriqueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

/**
 * Service métier du module de Ticketing Réclamations (Section 7).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReclamationService {

    private final ReclamationRepository reclamationRepository;
    private final ReclamationHistoriqueRepository historiqueRepository;
    private final MedecinRepository medecinRepository;
    private final NotificationService notificationService;

    public List<Reclamation> searchReclamations(String search, StatutReclamation statut,
                                                CategorieReclamation categorie, PrioriteReclamation priorite) {
        return reclamationRepository.searchReclamations(search, statut, categorie, priorite);
    }

    public List<Reclamation> getReclamationsByMedecin(Long medecinId) {
        Medecin m = medecinRepository.findById(medecinId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Médecin introuvable"));
        return reclamationRepository.findByMedecinOrderByDateDeclarationDesc(m);
    }

    public Reclamation getById(Long id) {
        return reclamationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Réclamation introuvable"));
    }

    @Transactional
    public Reclamation creerReclamation(ReclamationRequest request, String commercialUsername) {
        if (request.getDateVisite() == null || request.getDateVisite().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Une date de visite passée ou aujourd'hui est obligatoire.");
        }
        Medecin m = medecinRepository.findById(request.getMedecinId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Médecin introuvable"));

        Reclamation r = new Reclamation();
        r.setMedecin(m);
        r.setCommercialDeclarant(commercialUsername != null ? commercialUsername : "Commercial");
        r.setDateDeclaration(LocalDate.now());
        r.setDateVisite(request.getDateVisite());
        r.setDescription(request.getDescription());
        r.setCategorie(request.getCategorie() != null ? request.getCategorie() : CategorieReclamation.AUTRE);
        r.setPriorite(request.getPriorite() != null ? request.getPriorite() : PrioriteReclamation.MOYENNE);
        r.setStatut(StatutReclamation.A_TRAITER);
        r.setResponsableTraitement(request.getResponsableTraitement());
        r.setProchaineInstruction(request.getProchaineInstruction() != null
            ? request.getProchaineInstruction() : "Attendre le traitement par la direction.");
        r.setRetourTerrainId(request.getRetourTerrainId());
        r.setDateEcheance(request.getDateEcheance());

        log.info("Création d'un ticket réclamation pour le Dr {} par {}", m.getNom(), r.getCommercialDeclarant());
        Reclamation saved = reclamationRepository.save(r);
        enregistrerHistorique(saved, null, saved.getStatut(), saved.getDescription(), commercialUsername);
        notificationService.notifierDirectionPourReclamation(saved);
        return saved;
    }

    @Transactional
    public Reclamation mettreAJourStatut(Long id, StatutReclamation statut, String solution, String responsable) {
        return mettreAJourStatut(id, statut, solution, responsable, null, null,
            responsable == null || responsable.isBlank() ? "Direction" : responsable);
    }

    @Transactional
    public Reclamation mettreAJourStatut(Long id, StatutReclamation statut, String solution,
                                         String responsable, String prochaineInstruction, String auteur) {
        return mettreAJourStatut(id, statut, solution, responsable, prochaineInstruction, null, auteur);
    }

    @Transactional
    public Reclamation mettreAJourStatut(Long id, StatutReclamation statut, String solution,
                                         String responsable, String prochaineInstruction,
                                         LocalDate dateTraitement, String auteur) {
        Reclamation r = getById(id);
        StatutReclamation ancienStatut = r.getStatut();
        // Section 7 : commentaire obligatoire uniquement pour Traité et Clôturé sans action
        if ((statut == StatutReclamation.TRAITEE
            || statut == StatutReclamation.CLOTURE_SANS_ACTION)
                && (solution == null || solution.isBlank())) {
            throw new IllegalArgumentException("Le commentaire de réponse est obligatoire pour Traité ou Clôturé sans action.");
        }
        String responsableEffectif = responsable == null || responsable.isBlank()
                ? r.getResponsableTraitement()
                : responsable;
        if (statut == StatutReclamation.EN_COURS
                && (responsableEffectif == null || responsableEffectif.isBlank())) {
            throw new IllegalArgumentException("Un responsable doit être affecté avant le passage En cours.");
        }
        r.setStatut(statut);
        if (solution != null && !solution.isBlank()) r.setSolutionApportee(solution);
        if (responsable != null && !responsable.isBlank()) {
            r.setResponsableTraitement(responsable);
        }
        if (prochaineInstruction != null && !prochaineInstruction.isBlank()) {
            r.setProchaineInstruction(prochaineInstruction);
        }
        // Enregistrer la date de traitement (Section 7 document)
        if (dateTraitement != null) {
            r.setDateResolution(dateTraitement);
        } else if (statut == StatutReclamation.TRAITEE
            || statut == StatutReclamation.CLOTURE_SANS_ACTION
            || statut == StatutReclamation.RESOLUE
            || statut == StatutReclamation.REJETEE) {
            r.setDateResolution(LocalDate.now());
        }
        Reclamation saved = reclamationRepository.save(r);
        if (ancienStatut != statut) {
            enregistrerHistorique(saved, ancienStatut, statut,
                    solution == null || solution.isBlank() ? prochaineInstruction : solution, auteur);
        }
        return saved;
    }

    public List<ReclamationHistorique> getHistorique(Long reclamationId) {
        getById(reclamationId);
        return historiqueRepository.findByReclamation_IdOrderByCreatedAtDesc(reclamationId);
    }

    private void enregistrerHistorique(Reclamation reclamation, StatutReclamation ancienStatut,
                                       StatutReclamation nouveauStatut, String commentaire, String auteur) {
        ReclamationHistorique entree = new ReclamationHistorique();
        entree.setReclamation(reclamation);
        entree.setAncienStatut(ancienStatut);
        entree.setNouveauStatut(nouveauStatut);
        entree.setCommentaire(commentaire);
        entree.setAuteur(auteur == null || auteur.isBlank() ? "Système" : auteur);
        historiqueRepository.save(entree);
    }

    // Fixe (ou retire, si null) l'échéance de traitement d'une réclamation
    @Transactional
    public Reclamation definirEcheance(Long id, LocalDate dateEcheance) {
        Reclamation r = getById(id);
        r.setDateEcheance(dateEcheance);
        return reclamationRepository.save(r);
    }

    public long countOuvertes() {
        return reclamationRepository.countByStatut(StatutReclamation.A_TRAITER)
            + reclamationRepository.countByStatut(StatutReclamation.OUVERTE);
    }
}
