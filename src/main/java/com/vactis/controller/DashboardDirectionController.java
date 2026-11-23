package com.vactis.controller;

import com.vactis.dto.direction.DashboardDirectionResponse;
import com.vactis.service.DashboardDirectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Synthèse Direction : trajectoire, portefeuille à arbitrer, exécution commerciale, sujets à arbitrer
@RestController
@RequestMapping("/api/dashboard-direction")
@RequiredArgsConstructor
public class DashboardDirectionController {

    private final DashboardDirectionService dashboardDirectionService;

    @GetMapping
    public DashboardDirectionResponse getDashboard(@RequestParam(required = false) String mois) {
        return dashboardDirectionService.getDashboard(mois);
    }
}
