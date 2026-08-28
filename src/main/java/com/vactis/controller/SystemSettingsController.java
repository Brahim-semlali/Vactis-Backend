package com.vactis.controller;

import com.vactis.dto.system.SystemSettingsRequest;
import com.vactis.dto.system.SystemSettingsResponse;
import com.vactis.service.system.SystemSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class SystemSettingsController {
    private final SystemSettingsService service;

    @GetMapping
    public SystemSettingsResponse getSettings() {
        return service.getSettingsResponse();
    }

    @PutMapping
    public SystemSettingsResponse updateSettings(@Valid @RequestBody SystemSettingsRequest request) {
        return service.updateSettings(request);
    }

}