package com.vactis.model.auth;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.vactis.model.Roles.Roles;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
public class Users implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(unique = true)
    private String email;

    private String phone;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "account_locked", nullable = false)
    private boolean accountLocked = false;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    /** Durée de suspension en minutes (copiée depuis auth_settings lors du verrouillage). */
    @Column(name = "locked_until")
    private Integer lockedUntil;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @ManyToOne
    @JoinColumn(name = "idRole")
    private Roles roles;

    public LocalDateTime getLockEndTime() {
        if (lockedAt == null || lockedUntil == null) {
            return null;
        }
        return lockedAt.plusMinutes(lockedUntil);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (roles == null) {
            return List.of();
        }
        Set<GrantedAuthority> authorities = new HashSet<>();
        String roleName = roles.getNameRole().trim().toUpperCase(Locale.ROOT);
        authorities.add(new SimpleGrantedAuthority("ROLE_" + roleName));
        if (roles.getMenuItems() != null) {
            roles.getMenuItems().stream()
                .map(menu -> menu.getRoute())
                .filter(route -> route != null && !route.isBlank())
                .map(String::trim)
                .map(route -> new SimpleGrantedAuthority("MENU:" + route))
                .forEach(authorities::add);
        }
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        if (!accountLocked) {
            return true;
        }
        LocalDateTime endTime = getLockEndTime();
        if (endTime == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(endTime);
    }

    public boolean isSuspended() {
        if (!accountLocked) {
            return false;
        }
        LocalDateTime endTime = getLockEndTime();
        return endTime != null && LocalDateTime.now().isBefore(endTime);
    }

    @JsonProperty("status")
    public String getStatus() {
        if (!enabled) {
            return "DESACTIVE";
        }
        if (accountLocked && lockedAt == null) {
            return "BLOQUE";
        }
        if (isSuspended()) {
            return "SUSPENDU";
        }
        return "ACTIF";
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
