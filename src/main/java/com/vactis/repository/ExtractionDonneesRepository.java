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

    @Query("""
        select coalesce(sum(e.prixAPayer), 0L)
        from ExtractionDonnees e
        where e.dateReception >= :startDate and e.dateReception <= :endDate
    """)
    Long sumPrixAPayerByDateRange(@Param("startDate") java.time.LocalDate startDate, @Param("endDate") java.time.LocalDate endDate);

    @Query("""
        select count(e)
        from ExtractionDonnees e
        where e.dateReception >= :startDate and e.dateReception <= :endDate
    """)
    Long countCasByDateRange(@Param("startDate") java.time.LocalDate startDate, @Param("endDate") java.time.LocalDate endDate);

    @Query("""
        select count(distinct e.medecin.id)
        from ExtractionDonnees e
        where e.dateReception >= :startDate and e.dateReception <= :endDate and e.medecin is not null
    """)
    Long countMedecinsDistinctsByDateRange(@Param("startDate") java.time.LocalDate startDate, @Param("endDate") java.time.LocalDate endDate);

    @Query("""
        select coalesce(sum(e.prixAPayer), 0L)
        from ExtractionDonnees e
        where e.dateReception >= :startDate and e.dateReception <= :endDate and e.medecin is not null
    """)
    Long sumPrixAPayerWithMedecinByDateRange(@Param("startDate") java.time.LocalDate startDate, @Param("endDate") java.time.LocalDate endDate);

    @Query("""
        select count(e)
        from ExtractionDonnees e
        where e.dateReception >= :startDate and e.dateReception <= :endDate and e.medecin is null
    """)
    Long countNonAffectesByDateRange(@Param("startDate") java.time.LocalDate startDate, @Param("endDate") java.time.LocalDate endDate);

    @Query("""
        select coalesce(sum(e.prixAPayer), 0L)
        from ExtractionDonnees e
        where e.dateReception >= :startDate and e.dateReception <= :endDate and e.medecin is null
    """)
    Long sumPrixAPayerNonAffectesByDateRange(@Param("startDate") java.time.LocalDate startDate, @Param("endDate") java.time.LocalDate endDate);

    @Query("""
        select distinct e.dateReception
        from ExtractionDonnees e
        order by e.dateReception desc
    """)
    List<java.time.LocalDate> findAllDatesDescending();
}

