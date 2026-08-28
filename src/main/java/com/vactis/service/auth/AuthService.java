package com.vactis.service.auth;

import com.vactis.dto.auth.AccountStatusResponse;
import com.vactis.dto.auth.AuthResponse;
import com.vactis.dto.auth.LoginRequest;
import com.vactis.dto.auth.RegisterRequest;
import com.vactis.exception.AuthException;
import com.vactis.model.auth.AuthSettings;
import com.vactis.model.Roles.Roles;
import com.vactis.model.auth.Users;
import com.vactis.repository.RoleRepository;
import com.vactis.repository.auth.UserRepository;
import com.vactis.model.system.SystemSettings;
import com.vactis.service.system.ConnexionLogService;
import com.vactis.service.system.SystemSettingsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuthSettingsService authSettingsService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SystemSettingsService systemSettingsService;
    private final ConnexionLogService connexionLogService;

    public AuthResponse register(RegisterRequest request) {
        validatePassword(request.password(), currentSystemSettings());
        if (userRepository.existsByUsername(request.username())) {
            log.warn("[AUTH] Inscription refusée | username={} | raison=username_deja_utilise", request.username());
            throw AuthException.usernameTaken();
        }
        if (userRepository.existsByEmail(request.email())) {
            log.warn("[AUTH] Inscription refusée | email={} | raison=email_deja_utilise", request.email());
            throw AuthException.emailTaken();
        }

        log.info("[AUTH] Inscription | username={} | email={}", request.username(), request.email());
        Users user = new Users();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        Roles userRole = roleRepository.findByNameRoleIgnoreCase("USER")
            .orElseThrow(() -> new IllegalStateException("Le rôle USER n'existe pas"));
        user.setRoles(userRole);
        user.setEnabled(true);
        user.setAccountLocked(false);
        user.setFailedLoginAttempts(0);
        user.setLockedAt(null);
        user.setLockedUntil(null);
        userRepository.save(user);
        log.info("[AUTH] Inscription réussie | username={} | userId={}", request.username(), user.getId());
        return new AuthResponse(jwtService.generateToken(user));
    }

    public AuthResponse login(LoginRequest request) {
        return login(request, null);
    }

    public AuthResponse login(LoginRequest request, String ipAddress) {
        AuthSettings settings = authSettingsService.getSettings();
        SystemSettings systemSettings = currentSystemSettings();
        String username = request.username();

        log.info("[AUTH] Tentative connexion | username={} | maxTentatives={} | dureeSuspensionMin={}",
                username, settings.getMaxFailedAttempts(), settings.getLockDurationMinutes());

        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    logAttempt(null, false, ipAddress);
                    log.warn("[AUTH] Échec connexion | username={} | raison=utilisateur_inconnu", username);
                    return AuthException.badCredentials(null, null);
                });

        releaseExpiredLock(user);

        if (user.isAccountLocked() && user.getLockedAt() == null) {
            logAttempt(user, false, ipAddress);
            log.warn("[AUTH] Connexion refusée | username={} | raison=compte_bloque", username);
            throw AuthException.accountLocked(null, null, settings.getMaxFailedAttempts());
        }

        if (!user.isEnabled()) {
            logAttempt(user, false, ipAddress);
            log.warn("[AUTH] Connexion refusée | username={} | raison=compte_desactive", username);
            throw AuthException.accountDisabled();
        }

        if (user.isSuspended()) {
            logAttempt(user, false, ipAddress);
            log.warn("[AUTH] Connexion refusée | username={} | raison=compte_suspendu | minutes={} | fin={} | tentatives={}",
                    username, user.getLockedUntil(), user.getLockEndTime(), user.getFailedLoginAttempts());
            throw AuthException.accountLocked(user.getLockEndTime(), user.getLockedUntil(), settings.getMaxFailedAttempts());
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.password()));
        } catch (BadCredentialsException e) {
            logAttempt(user, false, ipAddress);
            throw handleFailedLogin(user, settings, systemSettings);
        } catch (LockedException e) {
            logAttempt(user, false, ipAddress);
            log.warn("[AUTH] Connexion refusée | username={} | raison=spring_locked", username);
            throw AuthException.accountLocked(
                    user.getLockEndTime(),
                    user.getLockedUntil(),
                    settings.getMaxFailedAttempts());
        } catch (DisabledException e) {
            logAttempt(user, false, ipAddress);
            log.warn("[AUTH] Connexion refusée | username={} | raison=spring_disabled", username);
            throw AuthException.accountDisabled();
        }

        handleSuccessfulLogin(user);
        logAttempt(user, true, ipAddress);
        log.info("[AUTH] Connexion réussie | username={} | userId={}", username, user.getId());
        return new AuthResponse(jwtService.generateToken(user));
    }

    private void releaseExpiredLock(Users user) {
        if (user.isAccountLocked()
            && user.getLockedAt() != null
            && user.getLockedUntil() != null
            && user.getLockedUntil() > 0
            && !user.isSuspended()) {
            user.setAccountLocked(false);
            user.setLockedAt(null);
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
            userRepository.saveAndFlush(user);
            log.info("[AUTH] Suspension expirée | username={} | compte_debloque", user.getUsername());
        }
    }

    private AuthException handleFailedLogin(Users user, AuthSettings settings, SystemSettings systemSettings) {
        Users freshUser = userRepository.findById(user.getId()).orElse(user);
        int maxAttempts = systemSettings == null ? settings.getMaxFailedAttempts() : systemSettings.getMaxTentativesConnexion();
        int lockMinutes = systemSettings == null ? settings.getLockDurationMinutes() : systemSettings.getDureeBlocageMinutes();
        int attempts = freshUser.getFailedLoginAttempts() + 1;
        freshUser.setFailedLoginAttempts(attempts);

        if (attempts >= maxAttempts) {
            freshUser.setAccountLocked(true);
            freshUser.setLockedAt(LocalDateTime.now());
            freshUser.setLockedUntil(lockMinutes == 0 ? null : lockMinutes);
            if (lockMinutes == 0) {
                freshUser.setLockedAt(null);
            }
            userRepository.saveAndFlush(freshUser);
            log.warn("[AUTH] Compte suspendu | username={} | tentatives={}/{} | dureeMin={} | fin={}",
                    freshUser.getUsername(), attempts, maxAttempts, lockMinutes, freshUser.getLockEndTime());
            return AuthException.accountLocked(freshUser.getLockEndTime(), lockMinutes, maxAttempts);
        }

        userRepository.saveAndFlush(freshUser);
        int remaining = maxAttempts - attempts;
        log.warn("[AUTH] Échec connexion | username={} | tentatives={}/{} | restantes={}",
                freshUser.getUsername(), attempts, maxAttempts, remaining);
        return AuthException.badCredentials(remaining, maxAttempts);
    }

    public AccountStatusResponse getAccountStatus(String username) {
        AuthSettings settings = authSettingsService.getSettings();
        SystemSettings systemSettings = currentSystemSettings();
        int maxAttempts = systemSettings == null ? settings.getMaxFailedAttempts() : systemSettings.getMaxTentativesConnexion();

        return userRepository.findByUsername(username)
                .map(user -> {
                    releaseExpiredLock(user);
                    boolean suspended = user.isSuspended();
                    int remaining = Math.max(0, maxAttempts - user.getFailedLoginAttempts());
                    LocalDateTime endTime = user.getLockEndTime();
                    return new AccountStatusResponse(
                            suspended,
                            endTime != null ? endTime.toString() : null,
                            user.getLockedUntil(),
                            user.getFailedLoginAttempts(),
                            maxAttempts,
                            suspended ? 0 : remaining,
                            computeRemainingSeconds(endTime));
                })
                .orElse(new AccountStatusResponse(false, null, null, 0, maxAttempts, maxAttempts, 0));
    }

    private static long computeRemainingSeconds(LocalDateTime lockEndTime) {
        if (lockEndTime == null) {
            return 0;
        }
        return Math.max(0, Duration.between(LocalDateTime.now(), lockEndTime).getSeconds());
    }

    private void handleSuccessfulLogin(Users user) {
        user.setFailedLoginAttempts(0);
        user.setAccountLocked(false);
        user.setLockedAt(null);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.saveAndFlush(user);
    }

    private SystemSettings currentSystemSettings() {
        return systemSettingsService == null ? null : systemSettingsService.getSettings();
    }

    private void logAttempt(Users user, boolean success, String ipAddress) {
        if (connexionLogService != null) {
            connexionLogService.logAttempt(user, success, ipAddress);
        }
    }

    public Optional<Long> findUserId(String username) {
        return userRepository.findByUsername(username).map(Users::getId);
    }

    private static void validatePassword(String password, SystemSettings settings) {
        if (settings == null) {
            return;
        }
        if (password == null || password.length() < settings.getMdpLongueurMinimale()
                || (settings.getMdpExigeMajuscule() && !password.matches(".*[A-Z].*"))
                || (settings.getMdpExigeChiffre() && !password.matches(".*\\d.*"))
                || (settings.getMdpExigeCaractereSpecial() && !password.matches(".*[^a-zA-Z0-9].*"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le mot de passe ne respecte pas la politique de sécurité configurée");
        }
    }
}
