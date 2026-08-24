package com.vactis.repository;

import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.RetourTerrain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// Repository JPA pour l'historique des retours terrain (visites médicales)
@Repository
public interface RetourTerrainRepository extends JpaRepository<RetourTerrain, Long> {

    @Modifying
    @Query("update RetourTerrain r set r.action = null where r.action is not null")
    int detachActions();

    // Retourne la dernière visite enregistrée pour un médecin (date DESC, created_at DESC)
    Optional<RetourTerrain> findFirstByMedecinOrderByDateVisiteDescCreatedAtDesc(Medecin medecin);

    // Retourne toutes les visites d'un médecin, de la plus récente à la plus ancienne
    List<RetourTerrain> findByMedecinOrderByDateVisiteDescCreatedAtDesc(Medecin medecin);

    // Retourne toutes les visites commerciales libres (hors VACTIS, action = null)
    List<RetourTerrain> findByActionIsNullOrderByDateVisiteDescCreatedAtDesc();

    // Retourne toutes les visites dont la date tombe dans une plage (inclusive)
    List<RetourTerrain> findByDateVisiteBetween(LocalDate start, LocalDate end);

    // Retourne la liste distincte de toutes les dates de visite enregistrées, triées par date décroissante
    @Query("""
        select distinct r.dateVisite
        from RetourTerrain r
        where r.dateVisite is not null
        order by r.dateVisite desc
    """)
    List<LocalDate> findAllDatesDescending();

    // ── Niveau 4 — Impact des visites terrain ────────────────────────────────

    /**
     * Retourne toutes les visites d'une plage avec JOIN FETCH sur médecin et action.
     * Évite les N+1 lors de l'accès à r.action et r.medecin en Niveau 4.
     */
    @Query("""
        select r from RetourTerrain r
        left join fetch r.action a
        join fetch r.medecin m
        where r.dateVisite between :start and :end
        order by r.dateVisite desc, m.nom, m.prenom
    """)
    List<RetourTerrain> findByDateVisiteBetweenWithFetch(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    /**
     * Retourne uniquement les visites VACTIS (action_id IS NOT NULL) sur une plage.
     * Utilisé pour le tableau détaillé et la classification par commercial (Niveau 4).
     */
    @Query("""
        select r from RetourTerrain r
        left join fetch r.action a
        join fetch r.medecin m
        where r.dateVisite between :start and :end
          and r.action is not null
        order by r.dateVisite desc, m.nom, m.prenom
    """)
    List<RetourTerrain> findVactisByDateVisiteBetween(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);
}



