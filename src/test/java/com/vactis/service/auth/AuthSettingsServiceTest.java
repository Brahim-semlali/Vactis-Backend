package com.vactis.service.auth;

import com.vactis.model.auth.AuthSettings;
import com.vactis.repository.auth.AuthSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthSettingsServiceTest {

    @Mock private AuthSettingsRepository repository;
    @InjectMocks private AuthSettingsService service;

    @Test
    void returnsStoredSettings() {
        AuthSettings settings = new AuthSettings();
        settings.setId(1L);
        settings.setMaxFailedAttempts(5);
        when(repository.findById(1L)).thenReturn(Optional.of(settings));

        assertEquals(settings, service.getSettings());
        verify(repository, never()).save(any());
    }

    @Test
    void createsDefaultsWhenSettingsAreMissing() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        when(repository.save(any(AuthSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthSettings settings = service.getSettings();

        assertEquals(1L, settings.getId());
        assertEquals(3, settings.getMaxFailedAttempts());
        assertEquals(2, settings.getLockDurationMinutes());
        verify(repository).save(any(AuthSettings.class));
    }
}
