package com.vactis.service.auth;

import com.vactis.dto.auth.AccountStatusResponse;
import com.vactis.dto.auth.AuthResponse;
import com.vactis.dto.auth.LoginRequest;
import com.vactis.dto.auth.RegisterRequest;
import com.vactis.exception.AuthException;
import com.vactis.model.auth.AuthSettings;
import com.vactis.model.auth.Role;
import com.vactis.model.auth.Users;
import com.vactis.repository.auth.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final AuthSettingsService authSettingsService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) {
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
        user.setRole(Role.USER);
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
        AuthSettings settings = authSettingsService.getSettings();
        String username = request.username();

        log.info("[AUTH] Tentative connexion | username={} | maxTentatives={} | dureeSuspensionMin={}",
                username, settings.getMaxFailedAttempts(), settings.getLockDurationMinutes());

        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("[AUTH] Échec connexion | username={} | raison=utilisateur_inconnu", username);
                    return AuthException.badCredentials(null, null);
                });

        releaseExpiredLock(user);

        if (!user.isEnabled()) {
            log.warn("[AUTH] Connexion refusée | username={} | raison=compte_desactive", username);
            throw AuthException.accountDisabled();
        }

        if (user.isSuspended()) {
            log.warn("[AUTH] Connexion refusée | username={} | raison=compte_suspendu | minutes={} | fin={} | tentatives={}",
                    username, user.getLockedUntil(), user.getLockEndTime(), user.getFailedLoginAttempts());
            throw AuthException.accountLocked(user.getLockEndTime(), user.getLockedUntil(), settings.getMaxFailedAttempts());
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.password()));
        } catch (BadCredentialsException e) {
            throw handleFailedLogin(user, settings);
        } catch (LockedException e) {
            log.warn("[AUTH] Connexion refusée | username={} | raison=spring_locked", username);
            throw AuthException.accountLocked(
                    user.getLockEndTime(),
                    user.getLockedUntil(),
                    settings.getMaxFailedAttempts());
        } catch (DisabledException e) {
            log.warn("[AUTH] Connexion refusée | username={} | raison=spring_disabled", username);
            throw AuthException.accountDisabled();
        }

        handleSuccessfulLogin(user);
        log.info("[AUTH] Connexion réussie | username={} | userId={}", username, user.getId());
        return new AuthResponse(jwtService.generateToken(user));
    }

    private void releaseExpiredLock(Users user) {
        if (user.isAccountLocked() && !user.isSuspended()) {
            user.setAccountLocked(false);
            user.setLockedAt(null);
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
            userRepository.saveAndFlush(user);
            log.info("[AUTH] Suspension expirée | username={} | compte_debloque", user.getUsername());
        }
    }

    private AuthException handleFailedLogin(Users user, AuthSettings settings) {
        Users freshUser = userRepository.findById(user.getId()).orElse(user);
        int maxAttempts = settings.getMaxFailedAttempts();
        int lockMinutes = settings.getLockDurationMinutes();
        int attempts = freshUser.getFailedLoginAttempts() + 1;
        freshUser.setFailedLoginAttempts(attempts);

        if (attempts >= maxAttempts) {
            freshUser.setAccountLocked(true);
            freshUser.setLockedAt(LocalDateTime.now());
            freshUser.setLockedUntil(lockMinutes);
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
        int maxAttempts = settings.getMaxFailedAttempts();

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
}
