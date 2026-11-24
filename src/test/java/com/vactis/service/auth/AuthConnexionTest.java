package com.vactis.service.auth;

import com.vactis.dto.auth.AccountStatusResponse;
import com.vactis.dto.auth.AuthResponse;
import com.vactis.dto.auth.LoginRequest;
import com.vactis.exception.AuthErrorCode;
import com.vactis.exception.AuthException;
import com.vactis.model.auth.AuthSettings;
import com.vactis.model.auth.Users;
import com.vactis.model.system.SystemSettings;
import com.vactis.repository.RoleRepository;
import com.vactis.repository.auth.UserRepository;
import com.vactis.service.system.ConnexionLogService;
import com.vactis.service.system.SystemSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Connexion et protection contre la force brute : tentatives restantes, suspension après N échecs,
 * blocage définitif, levée automatique de la suspension, journal des connexions.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthConnexionTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private AuthSettingsService authSettingsService;
    @Mock private JwtService jwtService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private SystemSettingsService systemSettingsService;
    @Mock private ConnexionLogService connexionLogService;

    @InjectMocks private AuthService authService;

    private Users user;
    private SystemSettings politique;

    @BeforeEach
    void setUp() {
        when(authSettingsService.getSettings()).thenReturn(new AuthSettings());
        politique = new SystemSettings();
        politique.setMaxTentativesConnexion(3);
        politique.setDureeBlocageMinutes(15);
        when(systemSettingsService.getSettings()).thenReturn(politique);

        user = new Users();
        user.setId(5L);
        user.setUsername("commercial1");
        when(userRepository.findByUsername("commercial1")).thenReturn(Optional.of(user));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(jwtService.generateToken(any())).thenReturn("jeton");
    }

    private AuthException echec(String motDePasse) {
        return assertThrows(AuthException.class, () -> authService.login(new LoginRequest("commercial1", motDePasse), "10.0.0.1"));
    }

    @Test
    void connexionReussieRemetLesCompteursAZero() {
        user.setFailedLoginAttempts(2);

        AuthResponse reponse = authService.login(new LoginRequest("commercial1", "bon"), "10.0.0.1");

        assertEquals("jeton", reponse.token());
        assertEquals(0, user.getFailedLoginAttempts());
        assertNotNull(user.getLastLoginAt());
        verify(connexionLogService).logAttempt(user, true, "10.0.0.1");
    }

    @Test
    @DisplayName("Chaque échec indique les tentatives restantes, puis le compte est suspendu à la 3e")
    void suspensionApresTroisEchecs() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("mauvais"));

        AuthException premier = echec("x");
        assertEquals(AuthErrorCode.BAD_CREDENTIALS, premier.getCode());
        assertEquals(2, premier.getRemainingAttempts());
        assertEquals(1, echec("x").getRemainingAttempts());

        AuthException troisieme = echec("x");
        assertEquals(AuthErrorCode.ACCOUNT_LOCKED, troisieme.getCode());
        assertTrue(user.isSuspended());
        assertEquals(15, user.getLockedUntil());

        // Pendant la suspension, même le bon mot de passe est refusé sans être vérifié
        AuthException pendant = echec("bon");
        assertEquals(AuthErrorCode.ACCOUNT_LOCKED, pendant.getCode());
        verify(connexionLogService, never()).logAttempt(any(), eq(true), any());
    }

    @Test
    @DisplayName("Durée de blocage 0 : le compte est bloqué jusqu'à intervention d'un administrateur")
    void blocageDefinitif() {
        politique.setDureeBlocageMinutes(0);
        user.setFailedLoginAttempts(2);
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("mauvais"));

        assertEquals(AuthErrorCode.ACCOUNT_LOCKED, echec("x").getCode());
        assertEquals("BLOQUE", user.getStatus());
        assertNull(user.getLockedAt());
        assertEquals(AuthErrorCode.ACCOUNT_LOCKED, echec("bon").getCode());
    }

    @Test
    void laSuspensionExpireeEstLeveeALaConnexion() {
        user.setAccountLocked(true);
        user.setLockedAt(LocalDateTime.now().minusMinutes(20));
        user.setLockedUntil(15);
        user.setFailedLoginAttempts(3);

        authService.login(new LoginRequest("commercial1", "bon"), null);

        assertFalse(user.isAccountLocked());
        assertEquals(0, user.getFailedLoginAttempts());
    }

    @Test
    void compteDesactiveRefuse() {
        user.setEnabled(false);
        assertEquals(AuthErrorCode.ACCOUNT_DISABLED, echec("bon").getCode());
        verify(authenticationManager, never()).authenticate(any());

        user.setEnabled(true);
        when(authenticationManager.authenticate(any())).thenThrow(new DisabledException("désactivé"));
        assertEquals(AuthErrorCode.ACCOUNT_DISABLED, echec("bon").getCode());
    }

    @Test
    @DisplayName("Utilisateur inconnu : même message qu'un mauvais mot de passe, sans compteur (pas d'énumération des comptes)")
    void utilisateurInconnu() {
        when(userRepository.findByUsername("inconnu")).thenReturn(Optional.empty());

        AuthException e = assertThrows(AuthException.class, () -> authService.login(new LoginRequest("inconnu", "x"), "10.0.0.2"));

        assertEquals(AuthErrorCode.BAD_CREDENTIALS, e.getCode());
        assertNull(e.getRemainingAttempts());
        verify(connexionLogService).logAttempt(null, false, "10.0.0.2");
    }

    @Test
    void etatDuCompteAvantConnexion() {
        user.setFailedLoginAttempts(1);
        AccountStatusResponse etat = authService.getAccountStatus("commercial1");
        assertFalse(etat.suspended());
        assertEquals(2, etat.remainingAttempts());

        user.setAccountLocked(true);
        user.setLockedAt(LocalDateTime.now());
        user.setLockedUntil(15);
        AccountStatusResponse suspendu = authService.getAccountStatus("commercial1");
        assertTrue(suspendu.suspended());
        assertEquals(0, suspendu.remainingAttempts());
        assertTrue(suspendu.remainingSeconds() > 800);

        when(userRepository.findByUsername("inconnu")).thenReturn(Optional.empty());
        assertEquals(3, authService.getAccountStatus("inconnu").remainingAttempts());
    }
}
