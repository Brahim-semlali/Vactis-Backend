package com.vactis.controller;

import com.vactis.dto.auth.AccountStatusResponse;
import com.vactis.dto.auth.AuthResponse;
import com.vactis.dto.auth.LoginRequest;
import com.vactis.dto.auth.RegisterRequest;
import com.vactis.service.auth.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import com.vactis.service.system.ConnexionLogService;
import org.springframework.security.core.Authentication;

// Contrôleur REST d'authentification : inscription, connexion et vérification de l'état du compte
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final ConnexionLogService connexionLogService;

    // Inscrit un nouvel utilisateur et retourne un token JWT
    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        log.info("[AUTH-API] POST /register | username={} | email={}",
                request.username(), request.email());
        return authService.register(request);
    }

    // Authentifie un utilisateur et retourne un token JWT en cas de succès
    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        log.info("[AUTH-API] POST /login | username={}", request.username());
        return authService.login(request, httpRequest.getRemoteAddr());
    }

    // Vérifie le statut du compte (suspension, tentatives échouées, etc.)
    @GetMapping("/account-status")
    public AccountStatusResponse accountStatus(@RequestParam String username) {
        log.info("[AUTH-API] GET /account-status | username={}", username);
        return authService.getAccountStatus(username);
    }

    @PostMapping("/logout")
    public void logout(Authentication authentication, HttpServletRequest httpRequest) {
        if (authentication != null) {
            authService.findUserId(authentication.getName()).ifPresent(connexionLogService::closeLatestLog);
        }
    }
}
