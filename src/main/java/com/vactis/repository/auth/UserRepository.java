package com.vactis.repository.auth;

import com.vactis.model.auth.Users;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

// Repository JPA pour la gestion des comptes utilisateurs
@Repository
public interface UserRepository extends JpaRepository<Users, Long> {

    // Recherche un utilisateur par son nom d'utilisateur
    Optional<Users> findByUsername(String username);

    // Vérifie si un nom d'utilisateur est déjà pris
    boolean existsByUsername(String username);

    // Vérifie si une adresse email est déjà enregistrée
    boolean existsByEmail(String email);
}
