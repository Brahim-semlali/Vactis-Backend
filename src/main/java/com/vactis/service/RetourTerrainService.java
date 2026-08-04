package com.vactis.service;

import com.vactis.dto.medecin.RetourTerrainRequest;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.RetourTerrain;
import com.vactis.repository.MedecinRepository;
import com.vactis.repository.RetourTerrainRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RetourTerrainService {

    private final RetourTerrainRepository retourTerrainRepository;
    private final MedecinRepository medecinRepository;

    /**
     * Crée une NOUVELLE ligne de retour terrain historisée.
     * Ne modifie ni n'écrase JAMAIS une ligne existante.
     */
    @Transactional
    public RetourTerrain addRetourTerrain(Long medecinId, RetourTerrainRequest request) {
        Medecin medecin = medecinRepository.findById(medecinId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Médecin introuvable."));

        if (request.getNote() == null || request.getNote() < 1.0 || request.getNote() > 5.0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La note doit être comprise entre 1 et 5.");
        }

        if (request.getDateVisite() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date de visite est obligatoire.");
        }

        if (request.getDateVisite().isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date de visite ne peut pas être dans le futur.");
        }

        RetourTerrain retour = new RetourTerrain();
        retour.setMedecin(medecin);
        retour.setNote(request.getNote());
        retour.setDateVisite(request.getDateVisite());
        retour.setCommentaire(request.getCommentaire() != null ? request.getCommentaire().trim() : null);

        // Détermination du visiteur : champ fourni > utilisateur connecté (SecurityContext) > fallback
        String visiteur = resolveVisiteur(request.getVisiteur());
        retour.setVisiteur(visiteur);

        // Sauvegarde d'une nouvelle instance (insert)
        return retourTerrainRepository.save(retour);
    }

    private String resolveVisiteur(String visiteurParam) {
        if (visiteurParam != null && !visiteurParam.isBlank()) {
            return visiteurParam.trim();
        }

        try {
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();

            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                Object principal = auth.getPrincipal();
                if (principal instanceof com.vactis.model.auth.Users user) {
                    String fullName = ((user.getFirstName() != null ? user.getFirstName() : "") + " " +
                            (user.getLastName() != null ? user.getLastName() : "")).trim();
                    if (!fullName.isEmpty()) {
                        return fullName;
                    }
                    return user.getUsername();
                } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
                    return userDetails.getUsername();
                }
                return auth.getName();
            }
        } catch (Exception e) {
            // Ignorer en cas d'accès hors contexte de sécurité
        }

        return null;
    }

    /**
     * Récupère toutes les visites d'un médecin par ordre chronologique décroissant.
     */
    public List<RetourTerrain> getRetoursTerrainByMedecin(Long medecinId) {
        Medecin medecin = medecinRepository.findById(medecinId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Médecin introuvable."));
        return retourTerrainRepository.findByMedecinOrderByDateVisiteDescCreatedAtDesc(medecin);
    }

    /**
     * Récupère la dernière visite enregistrée d'un médecin.
     */
    public Optional<RetourTerrain> getDerniereVisite(Long medecinId) {
        Medecin medecin = medecinRepository.findById(medecinId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Médecin introuvable."));
        return retourTerrainRepository.findFirstByMedecinOrderByDateVisiteDescCreatedAtDesc(medecin);
    }
}
