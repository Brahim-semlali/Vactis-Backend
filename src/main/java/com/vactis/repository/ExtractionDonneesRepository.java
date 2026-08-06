package com.vactis.repository;

import com.vactis.model.data.ExtractionDonnees;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

// Repository JPA pour l'accès et l'agrégation des dossiers d'analyses médicales
@Repository
public interface ExtractionDonneesRepository extends JpaRepository<ExtractionDonnees, Long> {

    // Compte le nombre de cas groupés par médecin
    @Query("""
        select e.medecin.id, coalesce(sum(e.nombreAnalyses), count(e))
        from ExtractionDonnees e
        where e.medecin is not null
        group by e.medecin.id
    """)
    List<Object[]> countCasGroupedByMedecin();

    // Compte le total de cas pour un médecin spécifique
    @Query("""
        select coalesce(sum(e.nombreAnalyses), count(e))
        from ExtractionDonnees e
        where e.medecin.id = :medecinId
    """)
    Long countCasByMedecinId(@Param("medecinId") Long medecinId);

    // Calcule le CA total (prix à payer) sur une période donnée
    @Query("""
        select coalesce(sum(e.prixAPayer), 0L)
        from ExtractionDonnees e
        where e.dateReception >= :startDate and e.dateReception <= :endDate
    """)
    Long sumPrixAPayerByDateRange(@Param("startDate") java.time.LocalDate startDate, @Param("endDate") java.time.LocalDate endDate);

    // Compte le nombre total de dossiers reçus sur une période
    @Query("""
        select count(e)
        from ExtractionDonnees e
        where e.dateReception >= :startDate and e.dateReception <= :endDate
    """)
    Long countCasByDateRange(@Param("startDate") java.time.LocalDate startDate, @Param("endDate") java.time.LocalDate endDate);

    // Compte le nombre de médecins distincts actifs sur une période
    @Query("""
        select count(distinct e.medecin.id)
        from ExtractionDonnees e
        where e.dateReception >= :startDate and e.dateReception <= :endDate and e.medecin is not null
    """)
    Long countMedecinsDistinctsByDateRange(@Param("startDate") java.time.LocalDate startDate, @Param("endDate") java.time.LocalDate endDate);

    // Calcule le CA des dossiers affectés à un médecin connu sur une période
    @Query("""
        select coalesce(sum(e.prixAPayer), 0L)
        from ExtractionDonnees e
        where e.dateReception >= :startDate and e.dateReception <= :endDate and e.medecin is not null
    """)
    Long sumPrixAPayerWithMedecinByDateRange(@Param("startDate") java.time.LocalDate startDate, @Param("endDate") java.time.LocalDate endDate);

    // Compte les dossiers non affectés à un médecin sur une période
    @Query("""
        select count(e)
        from ExtractionDonnees e
        where e.dateReception >= :startDate and e.dateReception <= :endDate and e.medecin is null
    """)
    Long countNonAffectesByDateRange(@Param("startDate") java.time.LocalDate startDate, @Param("endDate") java.time.LocalDate endDate);

    // Calcule le CA des dossiers non affectés sur une période
    @Query("""
        select coalesce(sum(e.prixAPayer), 0L)
        from ExtractionDonnees e
        where e.dateReception >= :startDate and e.dateReception <= :endDate and e.medecin is null
    """)
    Long sumPrixAPayerNonAffectesByDateRange(@Param("startDate") java.time.LocalDate startDate, @Param("endDate") java.time.LocalDate endDate);

    // Retourne toutes les dates de réception distinctes, triées du plus récent au plus ancien
    @Query("""
        select distinct e.dateReception
        from ExtractionDonnees e
        order by e.dateReception desc
    """)
    List<java.time.LocalDate> findAllDatesDescending();
}
