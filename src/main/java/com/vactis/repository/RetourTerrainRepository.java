package com.vactis.repository;

import com.vactis.model.medecin.Medecin;
import com.vactis.model.medecin.RetourTerrain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// Repository JPA pour l'historique des retours terrain (visites médicales)
@Repository
public interface RetourTerrainRepository extends JpaRepository<RetourTerrain, Long> {

    // Retourne la dernière visite enregistrée pour un médecin (date DESC, created_at DESC)
    Optional<RetourTerrain> findFirstByMedecinOrderByDateVisiteDescCreatedAtDesc(Medecin medecin);

    // Retourne toutes les visites d'un médecin, de la plus récente à la plus ancienne
    List<RetourTerrain> findByMedecinOrderByDateVisiteDescCreatedAtDesc(Medecin medecin);
}
