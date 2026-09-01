package com.vactis.controller;

import com.vactis.dto.auth.AccountStatusResponse;
import com.vactis.dto.auth.AuthResponse;
import com.vactis.dto.auth.LoginRequest;
import com.vactis.dto.auth.RegisterRequest;
import com.vactis.dto.auth.ChangePasswordRequest;
import com.vactis.dto.auth.UpdateProfileRequest;
import com.vactis.dto.auth.UserProfileResponse;
import com.vactis.service.auth.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import com.vactis.service.system.ConnexionLogService;
import com.vactis.service.system.SystemSettingsService;
import org.springframework.security.core.Authentication;

// Contrôleur REST d'authentification : inscription, connexion et vérification de l'état du compte
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final ConnexionLogService connexionLogService;
    private final SystemSettingsService systemSettingsService;

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

    @GetMapping("/profile")
    public UserProfileResponse getProfile(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Utilisateur non authentifié");
        }
        log.info("[AUTH-API] GET /profile | username={}", authentication.getName());
        return authService.getProfile(authentication.getName());
    }

    @PutMapping("/profile")
    public UserProfileResponse updateProfile(Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) {
        if (authentication == null || authentication.getName() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Utilisateur non authentifié");
        }
        log.info("[AUTH-API] PUT /profile | username={}", authentication.getName());
        return authService.updateProfile(authentication.getName(), request);
    }

    @PostMapping("/change-password")
    public void changePassword(Authentication authentication, @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(authentication.getName(), request);
    }

    @GetMapping("/password-policy")
    public com.vactis.dto.system.SystemSettingsResponse passwordPolicy() {
        return systemSettingsService.getSettingsResponse();
    }
}
