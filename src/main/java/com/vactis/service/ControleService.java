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

@Service
@RequiredArgsConstructor
public class ControleService {
    private final ControleRepository controleRepository;

    public List<Controle> findByType(TypeControle type) {
        return controleRepository.findByTypeOrderByMinCAAsc(type);
    }

    public Controle create(Controle controle) {
        validateControle(controle);
        return controleRepository.save(controle);
    }

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

    public void delete(Long idControle) {
        if (!controleRepository.existsById(idControle)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Règle de contrôle introuvable");
        }
        controleRepository.deleteById(idControle);
    }

    public String determinerEtat(TypeControle type, Long montant) {
        List<Controle> regles = controleRepository.findByTypeAndActifTrue(type);

        for (Controle regle : regles) {
            boolean montantSuperieurOuEgalAuMin = montant.compareTo(regle.getMinCA()) >= 0;
            boolean montantInferieurOuEgalAuMax = montant.compareTo(regle.getMaxCA()) <= 0;

            if (montantSuperieurOuEgalAuMin && montantInferieurOuEgalAuMax) {
                return regle.getEtat();
            }
        }

        return null;
    }

    public List<String> getEtatsActifs(TypeControle type) {
        return controleRepository.findByTypeAndActifTrue(type).stream()
                .map(Controle::getEtat)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

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
