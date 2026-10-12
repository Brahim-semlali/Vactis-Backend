package com.vactis.controller;

import com.vactis.dto.system.LaboratoireLocationRequest;
import com.vactis.service.system.SystemSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = LaboratoireLocationSecurityTest.TestConfig.class)
class LaboratoireLocationSecurityTest {

    @Autowired
    private LaboratoireLocationController controller;

    @Autowired
    private SystemSettingsService settingsService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanUpdateLaboratoryLocation() {
        LaboratoireLocationRequest request = new LaboratoireLocationRequest(31.63, -7.99);

        controller.updateLocation(request);

        verify(settingsService).updateLaboratoireLocation(request);
    }

    @Test
    @WithMockUser(roles = "USER")
    void nonAdminCannotUpdateLaboratoryLocation() {
        LaboratoireLocationRequest request = new LaboratoireLocationRequest(31.63, -7.99);

        assertThrows(AccessDeniedException.class, () -> controller.updateLocation(request));
    }

    @Configuration
    @EnableMethodSecurity
    @Import(LaboratoireLocationController.class)
    static class TestConfig {
        @Bean
        SystemSettingsService systemSettingsService() {
            return mock(SystemSettingsService.class);
        }
    }
}