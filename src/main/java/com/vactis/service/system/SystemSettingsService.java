package com.vactis.service.system;

import com.vactis.dto.system.SystemSettingsRequest;
import com.vactis.dto.system.SystemSettingsResponse;
import com.vactis.model.auth.Users;
import com.vactis.model.system.SystemSettings;
import com.vactis.repository.auth.UserRepository;
import com.vactis.repository.system.SystemSettingsRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SystemSettingsService {
    private final SystemSettingsRepository repository;
    private final UserRepository userRepository;

    @Transactional
    public SystemSettings getSettings() {
        return repository.findFirstOrCreateDefault();
    }

    @Transactional
    public SystemSettingsResponse getSettingsResponse() {
        return toResponse(getSettings());
    }

    @Transactional
    public SystemSettingsResponse updateSettings(SystemSettingsRequest request) {
        validate(request);
        SystemSettings settings = getSettings();
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Users admin = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Administrateur introuvable"));
        settings.setDureeSessionMinutes(request.dureeSessionMinutes());
        settings.setDureeInactiviteJours(request.dureeInactiviteJours());
        settings.setMdpLongueurMinimale(request.mdpLongueurMinimale());
        settings.setMdpExigeMajuscule(request.mdpExigeMajuscule());
        settings.setMdpExigeChiffre(request.mdpExigeChiffre());
        settings.setMdpExigeCaractereSpecial(request.mdpExigeCaractereSpecial());
        settings.setMdpExpirationJours(request.mdpExpirationJours());
        settings.setMaxTentativesConnexion(request.maxTentativesConnexion());
        settings.setDureeBlocageMinutes(request.dureeBlocageMinutes());
        settings.setJournalConnexionActif(request.journalConnexionActif());
        settings.setUpdatedAt(LocalDateTime.now());
        settings.setUpdatedBy(admin);
        return toResponse(repository.save(settings));
    }

    private static void validate(SystemSettingsRequest request) {
        if (request == null || request.dureeSessionMinutes() == null || request.dureeSessionMinutes() <= 0
                || request.dureeInactiviteJours() == null || request.dureeInactiviteJours() <= 0
                || request.mdpLongueurMinimale() == null || request.mdpLongueurMinimale() <= 0
                || request.mdpExpirationJours() == null || request.mdpExpirationJours() < 0
                || request.maxTentativesConnexion() == null || request.maxTentativesConnexion() <= 0
                || request.dureeBlocageMinutes() == null || request.dureeBlocageMinutes() < 0
                || request.mdpExigeMajuscule() == null || request.mdpExigeChiffre() == null
                || request.mdpExigeCaractereSpecial() == null || request.journalConnexionActif() == null) {
            throw new IllegalArgumentException("Les paramètres doivent respecter des valeurs positives; les expirations et blocages peuvent être à zéro");
        }
    }

    private static SystemSettingsResponse toResponse(SystemSettings settings) {
        return new SystemSettingsResponse(
                settings.getDureeSessionMinutes(),
                settings.getDureeInactiviteJours(),
                settings.getMdpLongueurMinimale(),
                settings.getMdpExigeMajuscule(),
                settings.getMdpExigeChiffre(),
                settings.getMdpExigeCaractereSpecial(),
                settings.getMdpExpirationJours(),
                settings.getMaxTentativesConnexion(),
                settings.getDureeBlocageMinutes(),
                settings.getJournalConnexionActif(),
                settings.getUpdatedAt(),
                settings.getUpdatedBy() == null ? null : settings.getUpdatedBy().getUsername());
    }
}