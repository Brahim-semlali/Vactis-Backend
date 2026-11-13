package com.vactis.service.system;

import com.vactis.dto.system.SystemSettingsRequest;
import com.vactis.dto.system.SystemSettingsResponse;
import com.vactis.dto.system.LaboratoireLocationRequest;
import com.vactis.model.auth.Users;
import com.vactis.model.system.SystemSettings;
import com.vactis.repository.ExtractionDonneesRepository;
import com.vactis.repository.auth.UserRepository;
import com.vactis.repository.system.SystemSettingsRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.Year;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class SystemSettingsService {
    private final SystemSettingsRepository repository;
    private final UserRepository userRepository;
    private final ExtractionDonneesRepository extractionDonneesRepository;

    @Transactional
    public SystemSettings getSettings() {
        return repository.findFirstOrCreateDefault();
    }

    @Transactional
    public SystemSettingsResponse getSettingsResponse() {
        SystemSettings settings = getSettings();
        SystemSettingsResponse response = toResponse(settings);
        if (response.bridgeGoalReferenceRevenue() != null) return response;
        Year previousYear = Year.now().minusYears(1);
        long previousRevenue = extractionDonneesRepository.sumPrixAPayerByDateRange(
            previousYear.atDay(1), previousYear.atMonth(12).atEndOfMonth());
        return new SystemSettingsResponse(response.dureeSessionMinutes(), response.dureeInactiviteJours(),
            response.mdpLongueurMinimale(), response.mdpExigeMajuscule(), response.mdpExigeChiffre(),
            response.mdpExigeCaractereSpecial(), response.maxTentativesConnexion(), response.dureeBlocageMinutes(),
            response.journalConnexionActif(), response.bridgeGoalTarget(), response.bridgeGoalMethod(),
            response.bridgeGoalInputValue(), previousRevenue, response.updatedAt(), response.updatedBy());
    }

    @Transactional
    public LaboratoireLocationRequest updateLaboratoireLocation(LaboratoireLocationRequest request) {
        SystemSettings settings = getSettings();
        settings.setLaboratoireLatitude(request.latitude());
        settings.setLaboratoireLongitude(request.longitude());
        settings.setUpdatedAt(LocalDateTime.now());
        repository.save(settings);
        return request;
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
        settings.setMaxTentativesConnexion(request.maxTentativesConnexion());
        settings.setDureeBlocageMinutes(request.dureeBlocageMinutes());
        settings.setJournalConnexionActif(request.journalConnexionActif());
        applyBridgeGoalSettings(settings, request);
        settings.setUpdatedAt(LocalDateTime.now());
        settings.setUpdatedBy(admin);
        return toResponse(repository.save(settings));
    }

    @Transactional
    public Long updateBridgeGoalTarget(Long target) {
        if (target == null || target <= 0) {
            throw new IllegalArgumentException("L'objectif Bridge to Goal doit être strictement positif");
        }

        return updateBridgeGoalTarget(BigDecimal.valueOf(target), "DIRECT");
    }

    @Transactional
    public Long updateBridgeGoalTarget(BigDecimal input, String method) {
        String normalizedMethod = method == null || method.isBlank() ? "DIRECT" : method.trim().toUpperCase();
        if (input == null || input.signum() < 0 || ("DIRECT".equals(normalizedMethod) && input.signum() == 0)) {
            throw new IllegalArgumentException("La valeur de l’objectif annuel est invalide.");
        }
        if (!"DIRECT".equals(normalizedMethod) && !"PERCENTAGE".equals(normalizedMethod)) {
            throw new IllegalArgumentException("La méthode annuelle doit être DIRECT ou PERCENTAGE.");
        }

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Users admin = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Administrateur introuvable"));

        SystemSettings settings = getSettings();
        applyBridgeGoalSettings(settings, normalizedMethod, input);
        settings.setUpdatedAt(LocalDateTime.now());
        settings.setUpdatedBy(admin);
        repository.save(settings);
        return settings.getBridgeGoalTarget();
    }

    private static void validate(SystemSettingsRequest request) {
        if (request == null || request.dureeSessionMinutes() == null || request.dureeSessionMinutes() <= 0
                || request.dureeInactiviteJours() == null || request.dureeInactiviteJours() <= 0
                || request.mdpLongueurMinimale() == null || request.mdpLongueurMinimale() <= 0
                || request.maxTentativesConnexion() == null || request.maxTentativesConnexion() <= 0
                || request.dureeBlocageMinutes() == null || request.dureeBlocageMinutes() < 0
                || request.mdpExigeMajuscule() == null || request.mdpExigeChiffre() == null
                || request.mdpExigeCaractereSpecial() == null || request.journalConnexionActif() == null
                || request.bridgeGoalTarget() == null || request.bridgeGoalTarget() <= 0) {
            throw new IllegalArgumentException("Les paramètres doivent respecter des valeurs positives; les expirations et blocages peuvent être à zéro");
        }
    }

    private void applyBridgeGoalSettings(SystemSettings settings, SystemSettingsRequest request) {
        String method = request.bridgeGoalMethod() == null || request.bridgeGoalMethod().isBlank()
                ? "DIRECT" : request.bridgeGoalMethod().trim().toUpperCase();
        BigDecimal input = request.bridgeGoalInputValue() == null
                ? BigDecimal.valueOf(request.bridgeGoalTarget()) : request.bridgeGoalInputValue();
        applyBridgeGoalSettings(settings, method, input);
        }

        private void applyBridgeGoalSettings(SystemSettings settings, String method, BigDecimal input) {
        if (input.signum() < 0 || ("DIRECT".equals(method) && input.signum() == 0)) {
            throw new IllegalArgumentException("La valeur de l’objectif annuel est invalide.");
        }

        if ("DIRECT".equals(method)) {
            long target = input.setScale(0, RoundingMode.HALF_UP).longValueExact();
            if (target <= 0) throw new IllegalArgumentException("L’objectif annuel doit être strictement positif.");
            settings.setBridgeGoalTarget(target);
            settings.setBridgeGoalMethod(method);
            settings.setBridgeGoalInputValue(input);
            settings.setBridgeGoalReferenceRevenue(null);
            return;
        }
        if (!"PERCENTAGE".equals(method)) {
            throw new IllegalArgumentException("La méthode annuelle doit être DIRECT ou PERCENTAGE.");
        }

        Year previousYear = Year.now().minusYears(1);
        Long revenue = extractionDonneesRepository.sumPrixAPayerByDateRange(
            previousYear.atDay(1), previousYear.atMonth(12).atEndOfMonth());
        long previousRevenue = revenue == null ? 0L : revenue;
        if (previousRevenue <= 0) {
            throw new IllegalArgumentException("Le CA de l’année précédente doit être positif pour calculer un objectif en pourcentage.");
        }
        long target = BigDecimal.valueOf(previousRevenue)
                .multiply(BigDecimal.ONE.add(input.movePointLeft(2)))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        if (target <= 0) throw new IllegalArgumentException("L’objectif annuel calculé doit être strictement positif.");

        settings.setBridgeGoalTarget(target);
        settings.setBridgeGoalMethod(method);
        settings.setBridgeGoalInputValue(input);
        settings.setBridgeGoalReferenceRevenue(previousRevenue);
    }

    private static SystemSettingsResponse toResponse(SystemSettings settings) {
        return new SystemSettingsResponse(
                settings.getDureeSessionMinutes(),
                settings.getDureeInactiviteJours(),
                settings.getMdpLongueurMinimale(),
                settings.getMdpExigeMajuscule(),
                settings.getMdpExigeChiffre(),
                settings.getMdpExigeCaractereSpecial(),
                settings.getMaxTentativesConnexion(),
                settings.getDureeBlocageMinutes(),
                settings.getJournalConnexionActif(),
                settings.getBridgeGoalTarget(),
                settings.getBridgeGoalMethod() == null ? "DIRECT" : settings.getBridgeGoalMethod(),
                settings.getBridgeGoalInputValue() == null && settings.getBridgeGoalTarget() != null
                    ? BigDecimal.valueOf(settings.getBridgeGoalTarget()) : settings.getBridgeGoalInputValue(),
                settings.getBridgeGoalReferenceRevenue(),
                settings.getUpdatedAt(),
                settings.getUpdatedBy() == null ? null : settings.getUpdatedBy().getUsername());
    }
}