package com.vactis.repository;

import com.vactis.model.data.ExtractionDonnees;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExtractionDonneesRepository extends JpaRepository<ExtractionDonnees, Long> {

    @Query("""
        select e.medecin.id, coalesce(sum(e.nombreAnalyses), count(e))
        from ExtractionDonnees e
        where e.medecin is not null
        group by e.medecin.id
    """)
    List<Object[]> countCasGroupedByMedecin();

    @Query("""
        select coalesce(sum(e.nombreAnalyses), count(e))
        from ExtractionDonnees e
        where e.medecin.id = :medecinId
    """)
    Long countCasByMedecinId(@Param("medecinId") Long medecinId);
}
