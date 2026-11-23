package com.vactis.repository;

import com.vactis.model.data.ExtractionDonnees;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;

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

    @Query("""
        select e.medecin.id, coalesce(sum(e.prixAPayer), 0L)
        from ExtractionDonnees e
        where e.medecin is not null
        group by e.medecin.id
    """)
    List<Object[]> sumCaGroupedByMedecin();

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

    // Compte le nombre total d'analyses reçues sur une période
    @Query("""
        select coalesce(sum(e.nombreAnalyses), 0)
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

        @Query("""
                select count(distinct e.medecin.id)
                from ExtractionDonnees e
                where e.dateReception >= :startDate and e.dateReception <= :endDate
                    and e.medecin.id in :medecinIds
        """)
        Long countIdentifiedDoctorsByDateRange(
                        @Param("startDate") java.time.LocalDate startDate,
                        @Param("endDate") java.time.LocalDate endDate,
                        @Param("medecinIds") Collection<Long> medecinIds);

    // Calcule le CA des dossiers affectés à un médecin connu sur une période
    @Query("""
        select coalesce(sum(e.prixAPayer), 0L)
        from ExtractionDonnees e
        where e.dateReception >= :startDate and e.dateReception <= :endDate and e.medecin is not null
    """)
    Long sumPrixAPayerWithMedecinByDateRange(@Param("startDate") java.time.LocalDate startDate, @Param("endDate") java.time.LocalDate endDate);

        @Query("""
                select coalesce(sum(e.prixAPayer), 0L)
                from ExtractionDonnees e
                where e.dateReception >= :startDate and e.dateReception <= :endDate
                    and e.medecin.id in :medecinIds
        """)
        Long sumPrixAPayerForIdentifiedDoctorsByDateRange(
                        @Param("startDate") java.time.LocalDate startDate,
                        @Param("endDate") java.time.LocalDate endDate,
                        @Param("medecinIds") Collection<Long> medecinIds);

    // Compte les analyses non affectées à un médecin sur une période
    @Query("""
        select coalesce(sum(e.nombreAnalyses), 0)
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

        @Query("""
        select coalesce(sum(e.prixAPayer), 0L)
        from ExtractionDonnees e
        where e.dateReception >= :startDate and e.dateReception <= :endDate
          and (e.medecin is null or e.medecin.id not in :medecinIds)
        """)
        Long sumPrixAPayerForUnidentifiedDoctorsByDateRange(
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate,
            @Param("medecinIds") Collection<Long> medecinIds);

        @Query("""
        select coalesce(sum(e.nombreAnalyses), 0)
        from ExtractionDonnees e
        where e.dateReception >= :startDate and e.dateReception <= :endDate
          and (e.medecin is null or e.medecin.id not in :medecinIds)
        """)
        Long countCasesForUnidentifiedDoctorsByDateRange(
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate,
            @Param("medecinIds") Collection<Long> medecinIds);

    // Retourne toutes les dates de réception distinctes, triées du plus récent au plus ancien
    @Query("""
        select distinct e.dateReception
        from ExtractionDonnees e
        order by e.dateReception desc
    """)
    List<java.time.LocalDate> findAllDatesDescending();

    boolean existsByReferenceDossier(String referenceDossier);

    @Modifying
    @Query("delete from ExtractionDonnees e where e.dateReception between :startDate and :endDate")
    int deleteByDateReceptionRange(@Param("startDate") java.time.LocalDate startDate,
                                   @Param("endDate") java.time.LocalDate endDate);

        List<ExtractionDonnees> findAllByReferenceDossierIn(Collection<String> references);

        @Query(value = """
                select e
                from ExtractionDonnees e left join e.medecin m
                where (:year is null or year(e.dateReception) = :year)
                    and (:month is null or month(e.dateReception) = :month)
                    and (:search is null or :search = '' or
                             lower(concat(coalesce(m.prenom, ''), ' ', coalesce(m.nom, ''), ' ',
                                                        coalesce(m.specialite, ''), ' ', coalesce(m.organisme, ''), ' ',
                                                        coalesce(e.typeAnalyse, ''), ' ', coalesce(e.lieuPrelevement, ''))) like lower(concat('%', :search, '%')))
                """,
                countQuery = """
                select count(e)
                from ExtractionDonnees e left join e.medecin m
                where (:year is null or year(e.dateReception) = :year)
                    and (:month is null or month(e.dateReception) = :month)
                    and (:search is null or :search = '' or
                             lower(concat(coalesce(m.prenom, ''), ' ', coalesce(m.nom, ''), ' ',
                                                        coalesce(m.specialite, ''), ' ', coalesce(m.organisme, ''), ' ',
                                                        coalesce(e.typeAnalyse, ''), ' ', coalesce(e.lieuPrelevement, ''))) like lower(concat('%', :search, '%')))
                """)
        Page<ExtractionDonnees> searchAdminRows(
                        @Param("year") Integer year,
                        @Param("month") Integer month,
                        @Param("search") String search,
                        Pageable pageable);

        @Query("""
                select e
                from ExtractionDonnees e left join fetch e.medecin m
                where (:year is null or year(e.dateReception) = :year)
                    and (:month is null or month(e.dateReception) = :month)
                    and (:search is null or :search = '' or
                             lower(concat(coalesce(m.prenom, ''), ' ', coalesce(m.nom, ''), ' ',
                                                        coalesce(m.specialite, ''), ' ', coalesce(m.organisme, ''), ' ',
                                                        coalesce(e.typeAnalyse, ''), ' ', coalesce(e.lieuPrelevement, ''))) like lower(concat('%', :search, '%')))
                order by e.dateReception desc, e.id desc
                """)
        List<ExtractionDonnees> findAllAdminRows(
                        @Param("year") Integer year,
                        @Param("month") Integer month,
                        @Param("search") String search);

    @Query("""
        select distinct year(e.dateReception)
        from ExtractionDonnees e
        where e.dateReception is not null
        order by year(e.dateReception) desc
    """)
    List<Integer> findDistinctYears();

    @Query("""
        select distinct month(e.dateReception)
        from ExtractionDonnees e
        where e.dateReception is not null and year(e.dateReception) = :year
        order by month(e.dateReception)
    """)
    List<Integer> findDistinctMonthsByYear(@Param("year") int year);

    List<ExtractionDonnees> findByDateReceptionBetweenOrderByDateReceptionDesc(java.time.LocalDate start, java.time.LocalDate end);

    // Historique complet des dossiers d'un médecin (chronologie d'activité du Suivi équipe)
    List<ExtractionDonnees> findByMedecinIdOrderByDateReceptionAsc(Long medecinId);

    // -------------------------------------------------------------------------
    // Requêtes Niveau 2 — Dynamique portefeuille médecins
    // -------------------------------------------------------------------------

    /**
     * Calcule le CA total (prix à payer) par médecin sur une période donnée.
     * Retourne une liste de tableaux [medecinId, caTotal].
     * Seuls les médecins avec au moins un dossier affecté sur la période sont inclus.
     */
    @Query("""
        select e.medecin.id, coalesce(sum(e.prixAPayer), 0L)
        from ExtractionDonnees e
        where e.dateReception >= :startDate and e.dateReception <= :endDate
          and e.medecin is not null
        group by e.medecin.id
    """)
    List<Object[]> sumCaByMedecinAndDateRange(
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate
    );

    /**
     * Compte le nombre d'analyses par médecin sur une période donnée.
     * Retourne une liste de tableaux [medecinId, nombreCas].
     * Seuls les médecins avec au moins un dossier affecté sur la période sont inclus.
     */
    @Query("""
        select e.medecin.id, coalesce(sum(e.nombreAnalyses), 0)
        from ExtractionDonnees e
        where e.dateReception >= :startDate and e.dateReception <= :endDate
          and e.medecin is not null
        group by e.medecin.id
    """)
    List<Object[]> countCasByMedecinAndDateRange(
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate
    );

    /**
     * Retourne les IDs distincts des médecins ayant eu au moins un dossier
     * sur la période donnée (utilisé pour distinguer "à réactiver" vs "exclu").
     */
    @Query("""
        select distinct e.medecin.id
        from ExtractionDonnees e
        where e.dateReception >= :startDate and e.dateReception <= :endDate
          and e.medecin is not null
    """)
    List<Long> findMedecinIdsWithActivityInRange(
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate
    );

    /**
     * Retourne la date de réception du dernier dossier médical (patient) envoyé au labo par le médecin.
     */
    @Query("""
        select max(e.dateReception)
        from ExtractionDonnees e
        where e.medecin.id = :medecinId
    """)
    java.time.LocalDate findMaxDateReceptionByMedecinId(@Param("medecinId") Long medecinId);

    @Query("""
        select e.dateReception
        from ExtractionDonnees e
        where e.medecin.id = :medecinId and e.dateReception is not null
        order by e.dateReception desc
    """)
    List<java.time.LocalDate> findDatesReceptionByMedecinId(@Param("medecinId") Long medecinId);

    /**
     * Retourne le nombre de mois distincts avec activité par médecin sur tout l'historique.
     * Retourne une liste de tableaux [medecinId, nbMoisDistincts].
     * Utilisé pour calculer le CA mensuel moyen sur l'intégralité de l'historique.
     */
    @Query("""
        select e.medecin.id,
               count(distinct function('DATE_TRUNC', 'month', e.dateReception))
        from ExtractionDonnees e
        where e.medecin is not null and e.dateReception is not null
        group by e.medecin.id
    """)
    List<Object[]> countMoisDistinctsGroupedByMedecin();

    @Query("""
        select e.medecin.id, min(e.dateReception)
        from ExtractionDonnees e
        where e.medecin is not null and e.dateReception is not null
        group by e.medecin.id
    """)
    List<Object[]> findPremiereDateReceptionGroupedByMedecin();

    @Query("""
        select e.medecin.id, max(e.dateReception)
        from ExtractionDonnees e
        where e.medecin is not null and e.dateReception is not null
        group by e.medecin.id
    """)
    List<Object[]> findDerniereDateReceptionGroupedByMedecin();

    /**
     * Somme du CA par médecin sur tout l'historique HORS mois courant.
     * Le mois courant est exclu du calcul du CA moyen utilisé pour le rang de performance.
     * :moisCourantDebut = premier jour du mois courant (inclus = exclu de la somme).
     */
    @Query("""
        select e.medecin.id, coalesce(sum(e.prixAPayer), 0L)
        from ExtractionDonnees e
        where e.medecin is not null
          and e.dateReception is not null
          and e.dateReception < :moisCourantDebut
        group by e.medecin.id
    """)
    List<Object[]> sumCaGroupedByMedecinBeforeDate(
            @Param("moisCourantDebut") java.time.LocalDate moisCourantDebut
    );

    /**
     * Nombre de mois distincts avec activité par médecin HORS mois courant.
     * :moisCourantDebut = premier jour du mois courant (inclus = exclu du comptage).
     */
    @Query("""
        select e.medecin.id,
               count(distinct function('DATE_TRUNC', 'month', e.dateReception))
        from ExtractionDonnees e
        where e.medecin is not null
          and e.dateReception is not null
          and e.dateReception < :moisCourantDebut
        group by e.medecin.id
    """)
    List<Object[]> countMoisDistinctsGroupedByMedecinBeforeDate(
            @Param("moisCourantDebut") java.time.LocalDate moisCourantDebut
    );
}
