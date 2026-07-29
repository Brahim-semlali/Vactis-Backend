package com.vactis.model.auth;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "auth_settings")
@Data
@NoArgsConstructor
public class AuthSettings {

    @Id
    private Long id = 1L;

    @Column(name = "max_failed_attempts", nullable = false)
    private int maxFailedAttempts = 3;

    @Column(name = "lock_duration_minutes", nullable = false)
    private int lockDurationMinutes = 2;
}
