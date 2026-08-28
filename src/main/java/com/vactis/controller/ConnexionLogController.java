package com.vactis.controller;

import com.vactis.model.system.ConnexionLog;
import com.vactis.service.system.ConnexionLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/connexion-logs")
@RequiredArgsConstructor
public class ConnexionLogController {
    private final ConnexionLogService service;

    @GetMapping
    public Page<ConnexionLog> getConnexionLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFin,
            Pageable pageable) {
        return service.search(userId, dateDebut, dateFin, pageable);
    }
}