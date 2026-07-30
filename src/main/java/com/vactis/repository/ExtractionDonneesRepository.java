package com.vactis.repository;

import com.vactis.model.data.ExtractionDonnees;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExtractionDonneesRepository extends JpaRepository<ExtractionDonnees, Long> {
}
