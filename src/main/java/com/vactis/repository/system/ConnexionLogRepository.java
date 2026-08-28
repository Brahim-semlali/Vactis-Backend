package com.vactis.repository.system;

import com.vactis.model.system.ConnexionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ConnexionLogRepository extends JpaRepository<ConnexionLog, Long>, JpaSpecificationExecutor<ConnexionLog> {
  Optional<ConnexionLog> findTopByUserIdAndDateDeconnexionIsNullOrderByDateConnexionDesc(Long userId);
}