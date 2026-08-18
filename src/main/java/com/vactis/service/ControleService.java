package com.vactis.service;

import com.vactis.model.Controle.Controle;
import com.vactis.model.Controle.TypeControle;
import com.vactis.repository.ControleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

// Service métier pour la gestion et l'application des règles de contrôle (seuils CA et scores)
@Service
@RequiredArgsConstructor
public class ControleService {
    private final ControleRepository controleRepository;

    // Retourne les règles d'un type donné, triées par CA minimum
    public List<Controle> findByType(TypeControle type) {
        return controleRepository.findByTypeOrderByMinCAAsc(type);
    }

    // Crée et valide une nouvelle règle de contrôle
    public Controle create(Controle controle) {
        validateControle(controle);
        return controleRepository.save(controle);
    }

    // Met à jour une règle de contrôle existante
    public Controle update(Long idControle, Controle payload) {
        Controle existing = controleRepository.findById(idControle)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Règle de contrôle introuvable"));

        // Mise à jour du type si fourni, sinon on conserve le type existant
        if (payload.getType() != null) {
            existing.setType(payload.getType());
        }
        existing.setEtat(payload.getEtat());
        existing.setMinCA(payload.getMinCA());
        existing.setMaxCA(payload.getMaxCA());
        existing.setActif(payload.getActif() != null ? payload.getActif() : true);

        validateControle(existing);
        return controleRepository.save(existing);
    }

    // Supprime une règle de contrôle par son identifiant
    public void delete(Long idControle) {
        if (!controleRepository.existsById(idControle)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Règle de contrôle introuvable");
        }
        controleRepository.deleteById(idControle);
    }

    // Détermine l'état dynamique correspondant à un pourcentage de variation (ou palier de contrôle)
    public String determinerEtat(TypeControle type, Long montant) {
        List<Controle> regles = controleRepository.findByTypeAndActifTrue(type);

        if (montant != null) {
            for (Controle regle : regles) {
                boolean montantSuperieurOuEgalAuMin = montant.compareTo(regle.getMinCA()) >= 0;
                boolean montantInferieurOuEgalAuMax = montant.compareTo(regle.getMaxCA()) <= 0;

                if (montantSuperieurOuEgalAuMin && montantInferieurOuEgalAuMax) {
                    return regle.getEtat();
                }
            }
        }

        // Fallback dynamique selon les formules métier VACTIS si aucune règle personnalisée ne s'applique
        if (type == TypeControle.STATUT && montant != null) {
            if (montant > 20) return "PROGRESSION";
            if (montant >= -10) return "ACTIF_STABLE";
            if (montant >= -40) return "SURVEILLANCE";
            if (montant >= -70) return "RETENTION";
            return "SILENCE_CRITIQUE";
        }

        return null;
    }

    // Détermine le segment (A/B/C/D) correspondant à un score de valeur (0-100)
    public String determinerEtatParScore(TypeControle type, Double score) {
        if (score == null) return null;
        List<Controle> regles = controleRepository.findByTypeAndActifTrue(type);

        for (Controle regle : regles) {
            double minScore = regle.getMinCA() != null ? regle.getMinCA().doubleValue() : 0.0;
            double maxScore = regle.getMaxCA() != null ? regle.getMaxCA().doubleValue() : 100.0;

            if (score >= minScore && score <= maxScore) {
                return regle.getEtat();
            }
        }

        // Fallback dynamique selon les seuils VACTIS (A >= 75, B >= 60, C >= 45, D < 45)
        if (type == TypeControle.SEGEMENTS) {
            if (score >= 75) return "A";
            if (score >= 60) return "B";
            if (score >= 45) return "C";
            return "D";
        }

        return null;
    }

    // Retourne les libellés d'états actifs distincts pour un type de règle (avec fallback dynamique)
    public List<String> getEtatsActifs(TypeControle type) {
        List<String> etats = controleRepository.findByTypeAndActifTrue(type).stream()
                .map(Controle::getEtat)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (!etats.isEmpty()) {
            return etats;
        }

        if (type == TypeControle.STATUT) {
            return List.of("PROGRESSION", "ACTIF_STABLE", "SURVEILLANCE", "RETENTION", "SILENCE_CRITIQUE");
        } else {
            return List.of("A", "B", "C", "D");
        }
    }

    // Valide la cohérence des champs d'une règle de contrôle avant enregistrement
    private void validateControle(Controle controle) {
        if (controle.getType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le type de contrôle est obligatoire");
        }
        if (controle.getEtat() == null || controle.getEtat().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'état est obligatoire");
        }
        if (controle.getMinCA() == null || controle.getMaxCA() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Les bornes CA min et max sont obligatoires");
        }
        if (controle.getMinCA() > controle.getMaxCA()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le CA min ne peut pas dépasser le CA max");
        }
        if (controle.getActif() == null) {
            controle.setActif(true);
        }
    }
}
