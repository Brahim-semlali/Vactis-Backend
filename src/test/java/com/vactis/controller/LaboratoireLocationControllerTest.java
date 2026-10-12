package com.vactis.controller;

import com.vactis.dto.system.LaboratoireLocationRequest;
import com.vactis.model.system.SystemSettings;
import com.vactis.service.system.SystemSettingsService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LaboratoireLocationControllerTest {

    private static Validator validator;
    private static ValidatorFactory validatorFactory;

    private final SystemSettingsService settingsService = mock(SystemSettingsService.class);
    private final LaboratoireLocationController controller = new LaboratoireLocationController(settingsService);

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void returnsNoContentWhenLaboratoryHasNoLocation() {
        when(settingsService.getSettings()).thenReturn(new SystemSettings());

        ResponseEntity<LaboratoireLocationRequest> response = controller.getLocation();

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void returnsSavedLaboratoryCoordinates() {
        SystemSettings settings = new SystemSettings();
        settings.setLaboratoireLatitude(31.63);
        settings.setLaboratoireLongitude(-7.99);
        when(settingsService.getSettings()).thenReturn(settings);

        ResponseEntity<LaboratoireLocationRequest> response = controller.getLocation();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(new LaboratoireLocationRequest(31.63, -7.99), response.getBody());
    }

    @Test
    void updatesCoordinatesThroughTheSettingsService() {
        LaboratoireLocationRequest request = new LaboratoireLocationRequest(31.63, -7.99);
        when(settingsService.updateLaboratoireLocation(request)).thenReturn(request);

        ResponseEntity<LaboratoireLocationRequest> response = controller.updateLocation(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(request, response.getBody());
        verify(settingsService).updateLaboratoireLocation(request);
    }

    @Test
    void updateEndpointRequiresAdminRole() throws NoSuchMethodException {
        PreAuthorize authorization = LaboratoireLocationController.class
                .getMethod("updateLocation", LaboratoireLocationRequest.class)
                .getAnnotation(PreAuthorize.class);

        assertEquals("hasRole('ADMIN')", authorization.value());
    }

    @Test
    void rejectsCoordinatesOutsideGeographicLimits() {
        Set<?> invalidLatitude = validator.validate(new LaboratoireLocationRequest(90.1, 0.0));
        Set<?> invalidLongitude = validator.validate(new LaboratoireLocationRequest(0.0, -180.1));
        Set<?> missingLatitude = validator.validate(new LaboratoireLocationRequest(null, 0.0));

        assertTrue(invalidLatitude.size() > 0);
        assertTrue(invalidLongitude.size() > 0);
        assertTrue(missingLatitude.size() > 0);
    }
}