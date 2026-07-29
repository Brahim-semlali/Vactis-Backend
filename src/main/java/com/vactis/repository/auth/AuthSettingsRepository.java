package com.vactis.repository.auth;

import com.vactis.model.auth.AuthSettings;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthSettingsRepository extends JpaRepository<AuthSettings, Long> {
}
