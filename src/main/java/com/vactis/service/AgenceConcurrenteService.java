package com.vactis.service;

import com.vactis.dto.concurrence.AgenceConcurrenteRequest;
import com.vactis.dto.concurrence.AgenceConcurrenteResponse;
import com.vactis.model.auth.Users;
import com.vactis.model.concurrence.AgenceConcurrente;
import com.vactis.repository.AgenceConcurrenteRepository;
import com.vactis.repository.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgenceConcurrenteService {

    private final AgenceConcurrenteRepository repository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<AgenceConcurrenteResponse> getAllAgences() {
        return repository.findAllByOrderByNomAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AgenceConcurrenteResponse getAgenceById(Long id) {
        AgenceConcurrente agence = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agence concurrente introuvable : " + id));
        return toResponse(agence);
    }

    @Transactional
    public AgenceConcurrenteResponse createAgence(AgenceConcurrenteRequest request) {
        validate(request);
        Users currentUser = getCurrentUser();

        AgenceConcurrente agence = AgenceConcurrente.builder()
                .nom(request.nom().trim())
                .enseigne(request.enseigne() != null ? request.enseigne().trim() : null)
                .latitude(request.latitude())
                .longitude(request.longitude())
                .adresse(request.adresse() != null ? request.adresse().trim() : null)
                .notes(request.notes() != null ? request.notes().trim() : null)
                .createdAt(LocalDateTime.now())
                .createdBy(currentUser)
                .updatedAt(LocalDateTime.now())
                .updatedBy(currentUser)
                .build();

        AgenceConcurrente saved = repository.save(agence);
        return toResponse(saved);
    }

    @Transactional
    public AgenceConcurrenteResponse updateAgence(Long id, AgenceConcurrenteRequest request) {
        validate(request);
        AgenceConcurrente agence = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agence concurrente introuvable : " + id));

        Users currentUser = getCurrentUser();
        agence.setNom(request.nom().trim());
        agence.setEnseigne(request.enseigne() != null ? request.enseigne().trim() : null);
        agence.setLatitude(request.latitude());
        agence.setLongitude(request.longitude());
        agence.setAdresse(request.adresse() != null ? request.adresse().trim() : null);
        agence.setNotes(request.notes() != null ? request.notes().trim() : null);
        agence.setUpdatedAt(LocalDateTime.now());
        agence.setUpdatedBy(currentUser);

        AgenceConcurrente updated = repository.save(agence);
        return toResponse(updated);
    }

    @Transactional
    public void deleteAgence(Long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agence concurrente introuvable : " + id);
        }
        repository.deleteById(id);
    }

    private void validate(AgenceConcurrenteRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Les données de l'agence sont requises");
        }
        if (request.nom() == null || request.nom().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nom de l'agence est obligatoire");
        }
        if (request.latitude() == null || request.latitude() < -90.0 || request.latitude() > 90.0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La latitude doit être comprise entre -90.0 et 90.0");
        }
        if (request.longitude() == null || request.longitude() < -180.0 || request.longitude() > 180.0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La longitude doit être comprise entre -180.0 et 180.0");
        }
    }

    private Users getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return null;
        }
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }

    private AgenceConcurrenteResponse toResponse(AgenceConcurrente agence) {
        String createdByUsername = agence.getCreatedBy() != null ? agence.getCreatedBy().getUsername() : null;
        String updatedByUsername = agence.getUpdatedBy() != null ? agence.getUpdatedBy().getUsername() : null;

        return new AgenceConcurrenteResponse(
                agence.getId(),
                agence.getNom(),
                agence.getEnseigne(),
                agence.getLatitude(),
                agence.getLongitude(),
                agence.getAdresse(),
                agence.getNotes(),
                agence.getCreatedAt(),
                createdByUsername,
                agence.getUpdatedAt(),
                updatedByUsername
        );
    }
}
