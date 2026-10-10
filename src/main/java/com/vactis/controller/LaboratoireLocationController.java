package com.vactis.controller;

import com.vactis.dto.system.LaboratoireLocationRequest;
import com.vactis.model.system.SystemSettings;
import com.vactis.service.system.SystemSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/zone-intelligence/laboratoire")
@RequiredArgsConstructor
public class LaboratoireLocationController {

    private final SystemSettingsService settingsService;

    @GetMapping
    public ResponseEntity<LaboratoireLocationRequest> getLocation() {
        SystemSettings settings = settingsService.getSettings();
        if (settings.getLaboratoireLatitude() == null || settings.getLaboratoireLongitude() == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(new LaboratoireLocationRequest(
                settings.getLaboratoireLatitude(), settings.getLaboratoireLongitude()));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LaboratoireLocationRequest> updateLocation(
            @Valid @RequestBody LaboratoireLocationRequest request
    ) {
        return ResponseEntity.ok(settingsService.updateLaboratoireLocation(request));
    }
}