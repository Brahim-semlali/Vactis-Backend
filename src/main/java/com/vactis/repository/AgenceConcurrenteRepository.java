package com.vactis.repository;

import com.vactis.model.concurrence.AgenceConcurrente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgenceConcurrenteRepository extends JpaRepository<AgenceConcurrente, Long> {
    List<AgenceConcurrente> findAllByOrderByNomAsc();
}
