package com.vactis.service;

import com.vactis.model.AuthSettings;
import com.vactis.repository.AuthSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthSettingsService {

    private static final long SETTINGS_ID = 1L;

    private final AuthSettingsRepository authSettingsRepository;

    public AuthSettings getSettings() {
        return authSettingsRepository.findById(SETTINGS_ID)
                .orElseGet(this::createDefaultSettings);
    }

    private AuthSettings createDefaultSettings() {
        log.warn("[AUTH-CONFIG] Paramètres absents en BDD, création des valeurs par défaut (3 tentatives, 2 min)");
        AuthSettings settings = new AuthSettings();
        settings.setId(SETTINGS_ID);
        settings.setMaxFailedAttempts(3);
        settings.setLockDurationMinutes(2);
        return authSettingsRepository.save(settings);
    }
}
