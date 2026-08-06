package com.vactis.repository;

import com.vactis.model.Controle.Controle;
import com.vactis.model.Controle.TypeControle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// Repository JPA pour les règles de contrôle (seuils CA, statuts et segments)
@Repository
public interface ControleRepository extends JpaRepository<Controle, Long> {

    // Retourne les règles actives d'un type donné
    List<Controle> findByTypeAndActifTrue(TypeControle type);

    // Retourne les règles d'un type, triées par CA minimum croissant
    List<Controle> findByTypeOrderByMinCAAsc(TypeControle type);

    // Recherche une règle par type et libellé d'état
    Optional<Controle> findByTypeAndEtat(TypeControle type , String etat);

}
