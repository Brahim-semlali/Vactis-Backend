package com.vactis.dto.concurrence;

import java.time.LocalDateTime;

public record AgenceConcurrenteResponse(
        Long id,
        String nom,
        String enseigne,
        Double latitude,
        Double longitude,
        String adresse,
        String notes,
        LocalDateTime createdAt,
        String createdBy,
        LocalDateTime updatedAt,
        String updatedBy
) {}
