package com.vactis.controller;

import com.vactis.dto.concurrence.AgenceConcurrenteRequest;
import com.vactis.dto.concurrence.AgenceConcurrenteResponse;
import com.vactis.service.AgenceConcurrenteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agences-concurrentes")
@RequiredArgsConstructor
public class AgenceConcurrenteController {

    private final AgenceConcurrenteService service;

    @GetMapping
    public ResponseEntity<List<AgenceConcurrenteResponse>> getAllAgences() {
        return ResponseEntity.ok(service.getAllAgences());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AgenceConcurrenteResponse> getAgenceById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getAgenceById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AgenceConcurrenteResponse> createAgence(@Valid @RequestBody AgenceConcurrenteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createAgence(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AgenceConcurrenteResponse> updateAgence(
            @PathVariable Long id,
            @Valid @RequestBody AgenceConcurrenteRequest request
    ) {
        return ResponseEntity.ok(service.updateAgence(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAgence(@PathVariable Long id) {
        service.deleteAgence(id);
        return ResponseEntity.noContent().build();
    }
}
