package com.vactis.service;

import com.vactis.dto.medecin.RetourTerrainRequest;
import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.QualificationVisite;
import com.vactis.model.medecin.RetourTerrain;
import com.vactis.model.medecin.StatutVisite;
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

// Service métier pour la saisie et la consultation des retours terrain (visites médicales)
@Service
@RequiredArgsConstructor
public class RetourTerrainService {

    private final RetourTerrainRepository retourTerrainRepository;
    private final MedecinRepository medecinRepository;

    // Crée un nouveau retour terrain historisé — ne modifie jamais une visite existante
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
        String nomComplet = ((medecin.getNom() != null ? medecin.getNom() : "") + " " +
                (medecin.getPrenom() != null ? medecin.getPrenom() : "")).trim();
        retour.setNomMedecin(nomComplet.isEmpty() ? null : nomComplet);
        retour.setNote(request.getNote());
        retour.setDateVisite(request.getDateVisite());
        retour.setCommentaire(request.getCommentaire() != null ? request.getCommentaire().trim() : null);

        // Détermination du visiteur : champ fourni > utilisateur connecté (SecurityContext) > fallback
        String visiteur = resolveVisiteur(request.getVisiteur());
        retour.setVisiteur(visiteur);

        // --- Niveau 3 : mapping des champs d'exécution terrain ---

        // Statut d'exécution (REALISEE par défaut si non fourni)
        retour.setStatutVisite(parseEnumSafe(
                StatutVisite.class,
                request.getStatutVisite(),
                StatutVisite.REALISEE,
                "Statut visite invalide. Valeurs acceptées : REALISEE, NON_REALISEE, NON_RENSEIGNE."
        ));

        // Qualification du retour (NON_RENSEIGNE par défaut si non fourni)
        retour.setQualification(parseEnumSafe(
                QualificationVisite.class,
                request.getQualification(),
                QualificationVisite.NON_RENSEIGNE,
                "Qualification invalide. Valeurs acceptées : FAVORABLE, DEFAVORABLE, NEUTRE, NON_RENSEIGNE."
        ));

        // Réclamation (false par défaut si non fourni)
        retour.setReclamation(request.getReclamation() != null ? request.getReclamation() : false);

        // Sauvegarde d'une nouvelle instance (insert)
        return retourTerrainRepository.save(retour);
    }

    // Résout l'identité du visiteur depuis la requête ou le contexte de sécurité Spring
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

    // Retourne toutes les visites d'un médecin, de la plus récente à la plus ancienne
    public List<RetourTerrain> getRetoursTerrainByMedecin(Long medecinId) {
        Medecin medecin = medecinRepository.findById(medecinId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Médecin introuvable."));
        return retourTerrainRepository.findByMedecinOrderByDateVisiteDescCreatedAtDesc(medecin);
    }

    // Retourne la dernière visite enregistrée pour un médecin
    public Optional<RetourTerrain> getDerniereVisite(Medecin medecin) {
        if (medecin == null) {
            return Optional.empty();
        }
        return retourTerrainRepository.findFirstByMedecinOrderByDateVisiteDescCreatedAtDesc(medecin);
    }

    // Retourne la dernière visite enregistrée pour un médecin identifié par son id
    public Optional<RetourTerrain> getDerniereVisite(Long medecinId) {
        Medecin medecin = medecinRepository.findById(medecinId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Médecin introuvable."));
        return getDerniereVisite(medecin);
    }

    /**
     * Convertit une chaîne en enum de manière sécurisée.
     * Retourne la valeur par défaut si la chaîne est null/vide.
     * Lève une ResponseStatusException 400 si la valeur est invalide (au lieu d'un 500 IllegalArgumentException).
     */
    private <E extends Enum<E>> E parseEnumSafe(Class<E> enumType, String raw, E defaultValue, String errorMessage) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(enumType, raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
        }
    }
}
