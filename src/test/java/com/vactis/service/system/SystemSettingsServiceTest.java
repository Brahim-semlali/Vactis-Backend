package com.vactis.service.system;

import com.vactis.dto.system.SystemSettingsRequest;
import com.vactis.dto.system.LaboratoireLocationRequest;
import com.vactis.model.auth.Users;
import com.vactis.model.system.SystemSettings;
import com.vactis.repository.auth.UserRepository;
import com.vactis.repository.system.SystemSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemSettingsServiceTest {
    @Mock private SystemSettingsRepository repository;
    @Mock private UserRepository userRepository;
    @InjectMocks private SystemSettingsService service;

    @Test
    void returnsAndRepairsLegacyNullSettings() {
        SystemSettings settings = new SystemSettings();
        when(repository.findFirstOrCreateDefault()).thenReturn(settings);

        assertEquals(60, service.getSettings().getDureeSessionMinutes());
        assertEquals(5, service.getSettings().getMaxTentativesConnexion());
    }

    @Test
    void rejectsNonPositiveSettings() {
        SystemSettingsRequest request = new SystemSettingsRequest(0, 90, 8, false, false, false, 5, 15, true, 5000000L);

        assertThrows(IllegalArgumentException.class, () -> service.updateSettings(request));
    }

    @Test
    void updatesAllSettingsForCurrentAdmin() {
        Users admin = new Users();
        admin.setUsername("admin");
        SystemSettings settings = new SystemSettings();
        when(repository.findFirstOrCreateDefault()).thenReturn(settings);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(repository.save(any(SystemSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));
        org.springframework.security.core.context.SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("admin", null, java.util.Collections.emptyList()));
        SecurityContextHolder.setContext(context);

        SystemSettingsRequest request = new SystemSettingsRequest(30, 12, 12, true, true, true, 3, 0, false, 5000000L);
        var response = service.updateSettings(request);

        assertEquals(30, response.dureeSessionMinutes());
        assertEquals(12, response.dureeInactiviteJours());
        assertEquals(12, response.mdpLongueurMinimale());
        assertEquals("admin", response.updatedBy());
        SecurityContextHolder.clearContext();
    }

    @Test
    void updatesLaboratoryCoordinatesWithoutChangingOtherSettings() {
        SystemSettings settings = new SystemSettings();
        when(repository.findFirstOrCreateDefault()).thenReturn(settings);
        when(repository.save(any(SystemSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LaboratoireLocationRequest location = new LaboratoireLocationRequest(31.63, -7.99);
        var response = service.updateLaboratoireLocation(location);

        assertEquals(31.63, settings.getLaboratoireLatitude());
        assertEquals(-7.99, settings.getLaboratoireLongitude());
        assertEquals(location, response);
        assertEquals(60, settings.getDureeSessionMinutes());
    }
}