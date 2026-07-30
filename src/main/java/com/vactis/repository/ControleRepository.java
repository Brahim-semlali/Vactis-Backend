package com.vactis.repository;

import com.vactis.model.Controle.Controle;
import com.vactis.model.Controle.TypeControle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ControleRepository extends JpaRepository<Controle, Long> {

    List<Controle> findByTypeAndActifTrue(TypeControle type);

    List<Controle> findByTypeOrderByMinCAAsc(TypeControle type);

    Optional<Controle> findByTypeAndEtat(TypeControle type , String etat);

}
