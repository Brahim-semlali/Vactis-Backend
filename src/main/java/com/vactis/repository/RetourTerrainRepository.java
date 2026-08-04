package com.vactis.repository;

import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.RetourTerrain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RetourTerrainRepository extends JpaRepository<RetourTerrain, Long> {
    Optional<RetourTerrain> findFirstByMedecinOrderByDateVisiteDescCreatedAtDesc(Medecin medecin);
}
