package com.vactis.repository;

import com.vactis.model.alerte.AlerteHebdomadaire;
import com.vactis.model.alerte.StatutAlerte;
import com.vactis.model.medecin.Medecin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AlerteHebdomadaireRepository extends JpaRepository<AlerteHebdomadaire, Long> {

    List<AlerteHebdomadaire> findByStatutAlerteOrderByScoreUrgenceSilenceDesc(StatutAlerte statutAlerte);

    List<AlerteHebdomadaire> findByMedecinOrderByDateDetectionDesc(Medecin medecin);

    long countByStatutAlerte(StatutAlerte statutAlerte);

    @Query("""
        select a from AlerteHebdomadaire a
        where a.medecin = :medecin and a.dateDetection >= :dateMin and a.statutAlerte = :statut
    """)
    Optional<AlerteHebdomadaire> findRecentActiveAlerte(
            @Param("medecin") Medecin medecin,
            @Param("dateMin") LocalDate dateMin,
            @Param("statut") StatutAlerte statut
    );
}
