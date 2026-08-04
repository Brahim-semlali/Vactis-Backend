package com.vactis.repository;

import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.RetourTerrain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RetourTerrainRepository extends JpaRepository<RetourTerrain, Long> {

    /** Dernière visite enregistrée pour un médecin (priorité date_visite DESC puis created_at DESC). */
    Optional<RetourTerrain> findFirstByMedecinOrderByDateVisiteDescCreatedAtDesc(Medecin medecin);

    /** Toutes les visites d'un médecin, triées de la plus récente à la plus ancienne. */
    List<RetourTerrain> findByMedecinOrderByDateVisiteDescCreatedAtDesc(Medecin medecin);
}
