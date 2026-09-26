package com.vactis.service;

import com.vactis.dto.reclamation.ReclamationRequest;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.reclamation.CategorieReclamation;
import com.vactis.model.reclamation.PrioriteReclamation;
import com.vactis.model.reclamation.Reclamation;
import com.vactis.model.reclamation.StatutReclamation;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.ReclamationRepository;
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
    private final MedecinRepository medecinRepository;

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
        Medecin m = medecinRepository.findById(request.getMedecinId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Médecin introuvable"));

        Reclamation r = new Reclamation();
        r.setMedecin(m);
        r.setCommercialDeclarant(commercialUsername != null ? commercialUsername : "Commercial");
        r.setDateDeclaration(LocalDate.now());
        r.setDescription(request.getDescription());
        r.setCategorie(request.getCategorie() != null ? request.getCategorie() : CategorieReclamation.AUTRE);
        r.setPriorite(request.getPriorite() != null ? request.getPriorite() : PrioriteReclamation.MOYENNE);
        r.setStatut(StatutReclamation.OUVERTE);
        r.setResponsableTraitement(request.getResponsableTraitement());
        r.setRetourTerrainId(request.getRetourTerrainId());

        log.info("Création d'un ticket réclamation pour le Dr {} par {}", m.getNom(), r.getCommercialDeclarant());
        return reclamationRepository.save(r);
    }

    @Transactional
    public Reclamation mettreAJourStatut(Long id, StatutReclamation statut, String solution, String responsable) {
        Reclamation r = getById(id);
        if ((statut == StatutReclamation.RESOLUE
            || statut == StatutReclamation.REJETEE
            || statut == StatutReclamation.CLOTURE_SANS_ACTION)
                && (solution == null || solution.isBlank())) {
            throw new IllegalArgumentException("Le commentaire de réponse est obligatoire pour clôturer une réclamation.");
        }
        r.setStatut(statut);
        if (solution != null && !solution.isBlank()) r.setSolutionApportee(solution);
        if (responsable != null && !responsable.isBlank()) {
            r.setResponsableTraitement(responsable);
        }
        if (statut == StatutReclamation.RESOLUE
            || statut == StatutReclamation.REJETEE
            || statut == StatutReclamation.CLOTURE_SANS_ACTION) {
            r.setDateResolution(LocalDate.now());
        }
        return reclamationRepository.save(r);
    }

    public long countOuvertes() {
        return reclamationRepository.countByStatut(StatutReclamation.OUVERTE);
    }
}
