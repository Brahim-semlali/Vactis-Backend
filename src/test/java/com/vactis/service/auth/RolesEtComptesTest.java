package com.vactis.service.auth;

import com.vactis.dto.auth.UserAdminRequest;
import com.vactis.model.Roles.Roles;
import com.vactis.model.auth.Users;
import com.vactis.model.menu.MenuItem;
import com.vactis.model.system.SystemSettings;
import com.vactis.repository.RoleRepository;
import com.vactis.repository.auth.UserRepository;
import com.vactis.service.system.SystemSettingsService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Gestion des rôles et des comptes : traduction d'un rôle en droits (ROLE_x, MENU:/route),
 * états du compte (actif, suspendu, bloqué, désactivé) et opérations d'administration.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RolesEtComptesTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private SystemSettingsService systemSettingsService;

    @InjectMocks private UserDetailsServiceImpl service;

    private static Roles role(String nom, String... routes) {
        Roles r = new Roles();
        r.setIdRole(2L);
        r.setNameRole(nom);
        List<MenuItem> menus = new ArrayList<>();
        for (String route : routes) {
            MenuItem m = new MenuItem();
            m.setRoute(route);
            menus.add(m);
        }
        r.setMenuItems(menus);
        return r;
    }

    private static Set<String> droits(Users u) {
        return u.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    @Nested
    @DisplayName("Un rôle se traduit en droits")
    class Droits {

        @Test
        void leRoleDonneROLEEtLesRoutesDeSesMenus() {
            Users u = new Users();
            u.setRoles(role(" commerciale ", "/actions", " /medecins ", "", null));

            assertEquals(Set.of("ROLE_COMMERCIALE", "MENU:/actions", "MENU:/medecins"), droits(u));
        }

        @Test
        void sansRoleAucunDroit() {
            assertTrue(new Users().getAuthorities().isEmpty());
        }

        @Test
        void unRoleSansMenuNeDonneQueROLE() {
            Users u = new Users();
            Roles r = role("direction");
            r.setMenuItems(null);
            u.setRoles(r);
            assertEquals(Set.of("ROLE_DIRECTION"), droits(u));
        }
    }

    @Nested
    @DisplayName("États du compte")
    class Etats {

        @Test
        void compteActif() {
            Users u = new Users();
            assertTrue(u.isAccountNonLocked());
            assertEquals("ACTIF", u.getStatus());
        }

        @Test
        void compteDesactive() {
            Users u = new Users();
            u.setEnabled(false);
            assertFalse(u.isEnabled());
            assertEquals("DESACTIVE", u.getStatus());
        }

        @Test
        void compteBloqueSansFin() {
            Users u = new Users();
            u.setAccountLocked(true);
            assertFalse(u.isAccountNonLocked());
            assertEquals("BLOQUE", u.getStatus());
        }

        @Test
        void suspensionEnCoursPuisLevee() {
            Users u = new Users();
            u.setAccountLocked(true);
            u.setLockedAt(LocalDateTime.now().minusMinutes(5));
            u.setLockedUntil(30);
            assertFalse(u.isAccountNonLocked());
            assertTrue(u.isSuspended());
            assertEquals("SUSPENDU", u.getStatus());

            u.setLockedAt(LocalDateTime.now().minusMinutes(31));
            assertTrue(u.isAccountNonLocked());
            assertFalse(u.isSuspended());
        }
    }

    @Nested
    @DisplayName("Administration des comptes")
    class Administration {

        @Test
        void utilisateurInconnuAuChargement() {
            when(userRepository.findByUsernameWithRoleMenus("fantome")).thenReturn(Optional.empty());
            assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("fantome"));
        }

        @Test
        void suspendreBloquerPuisDebloquer() {
            Users u = new Users();
            when(userRepository.findById(5L)).thenReturn(Optional.of(u));

            service.suspendUser(5L, 15);
            assertTrue(u.isSuspended());
            assertEquals(15, u.getLockedUntil());

            service.blockUser(5L);
            assertEquals("BLOQUE", u.getStatus());
            assertNull(u.getLockedUntil());

            service.unblockUser(5L);
            assertEquals("ACTIF", u.getStatus());
            assertTrue(u.isAccountNonLocked());
        }

        @Test
        void uneSuspensionDoitAvoirUneDureePositive() {
            ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.suspendUser(5L, 0));
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
            verify(userRepository, never()).saveAndFlush(any());
        }

        @Test
        void operationsSurUnUtilisateurInconnu() {
            when(userRepository.findById(9L)).thenReturn(Optional.empty());
            when(userRepository.existsById(9L)).thenReturn(false);
            assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> service.blockUser(9L)).getStatusCode());
            assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> service.deleteUser(9L)).getStatusCode());
            assertThrows(EntityNotFoundException.class, () -> service.assignRole(9L, 2L));
        }

        @Test
        void attribuerUnRole() {
            Users u = new Users();
            Roles direction = role("DIRECTION", "/dashboard-direction");
            when(userRepository.findById(5L)).thenReturn(Optional.of(u));
            when(roleRepository.findById(2L)).thenReturn(Optional.of(direction));

            service.assignRole(5L, 2L);

            assertSame(direction, u.getRoles());
            assertTrue(droits(u).contains("MENU:/dashboard-direction"));
            verify(userRepository).save(u);
        }

        @Test
        void attribuerUnRoleInexistant() {
            when(userRepository.findById(5L)).thenReturn(Optional.of(new Users()));
            when(roleRepository.findById(99L)).thenReturn(Optional.empty());
            assertThrows(EntityNotFoundException.class, () -> service.assignRole(5L, 99L));
        }

        @Test
        void laPolitiqueDeMotDePasseEstAppliqueeALaCreation() {
            SystemSettings politique = new SystemSettings();
            politique.setMdpLongueurMinimale(10);
            politique.setMdpExigeMajuscule(true);
            politique.setMdpExigeChiffre(true);
            politique.setMdpExigeCaractereSpecial(true);
            when(systemSettingsService.getSettings()).thenReturn(politique);

            for (String faible : List.of("Court1!", "sansmajuscule1!", "SansChiffre!!", "SansSpecial123")) {
                UserAdminRequest demande = new UserAdminRequest("nouveau", faible, "A", "B", "a@b.ma", null, true, null);
                assertEquals(HttpStatus.BAD_REQUEST,
                        assertThrows(ResponseStatusException.class, () -> service.createUser(demande)).getStatusCode(), faible);
            }

            when(passwordEncoder.encode("Robuste-2026")).thenReturn("hash");
            when(userRepository.save(any(Users.class))).thenAnswer(i -> i.getArgument(0));
            Users cree = service.createUser(new UserAdminRequest("nouveau", "Robuste-2026", "A", "B", "a@b.ma", null, null, null));
            assertEquals("hash", cree.getPassword());
            assertTrue(cree.isEnabled());
        }

        @Test
        void emailDejaUtilise() {
            when(userRepository.existsByUsername("nouveau")).thenReturn(false);
            when(userRepository.existsByEmail("pris@b.ma")).thenReturn(true);
            UserAdminRequest demande = new UserAdminRequest("nouveau", "Robuste-2026", "A", "B", "pris@b.ma", null, true, null);
            assertThrows(ResponseStatusException.class, () -> service.createUser(demande));
            verify(userRepository, never()).save(any());
        }
    }
}
