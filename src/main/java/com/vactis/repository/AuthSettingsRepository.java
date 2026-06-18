package com.vactis.repository;

import com.vactis.model.AuthSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthSettingsRepository extends JpaRepository<AuthSettings, Long> {
}
