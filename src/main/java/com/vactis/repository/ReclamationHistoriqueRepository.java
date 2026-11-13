package com.vactis.repository;

import com.vactis.model.reclamation.ReclamationHistorique;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReclamationHistoriqueRepository extends JpaRepository<ReclamationHistorique, Long> {
    List<ReclamationHistorique> findByReclamation_IdOrderByCreatedAtDesc(Long reclamationId);
}