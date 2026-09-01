package com.vactis.service.auth;

import com.vactis.dto.auth.RegisterRequest;
import com.vactis.dto.auth.UpdateProfileRequest;
import com.vactis.dto.auth.UserProfileResponse;
import com.vactis.model.Roles.Roles;
import com.vactis.model.auth.Users;
import com.vactis.model.system.SystemSettings;
import com.vactis.dto.auth.ChangePasswordRequest;
import com.vactis.service.system.ConnexionLogService;
import com.vactis.service.system.SystemSettingsService;
import com.vactis.repository.RoleRepository;
import com.vactis.repository.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private AuthSettingsService authSettingsService;
    @Mock private JwtService jwtService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private org.springframework.security.authentication.AuthenticationManager authenticationManager;
    @Mock private SystemSettingsService systemSettingsService;
    @Mock private ConnexionLogService connexionLogService;

    @InjectMocks private AuthService authService;

    @Test
    void registerAssignsUserRoleAndReturnsToken() {
        RegisterRequest request = new RegisterRequest("alice", "secret1", "Alice", "Martin", "alice@test.local", null);
        Roles userRole = new Roles();
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@test.local")).thenReturn(false);
        when(roleRepository.findByNameRoleIgnoreCase("USER")).thenReturn(java.util.Optional.of(userRole));
        when(passwordEncoder.encode("secret1")).thenReturn("encoded");
        when(jwtService.generateToken(any(Users.class))).thenReturn("token");

        var response = authService.register(request);

        assertEquals("token", response.token());
        verify(userRepository).save(any(Users.class));
        verify(jwtService).generateToken(any(Users.class));
    }

    @Test
    void registerRejectsDuplicateUsername() {
        RegisterRequest request = new RegisterRequest("alice", "secret1", "Alice", "Martin", "alice@test.local", null);
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThrows(RuntimeException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePasswordRejectsIncorrectCurrentPassword() {
        Users user = new Users();
        user.setUsername("alice");
        user.setPassword("encoded-old");
        when(userRepository.findByUsername("alice")).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded-old")).thenReturn(false);

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> authService.changePassword("alice", new ChangePasswordRequest("wrong", "Newpass1!")));
        verify(userRepository, never()).save(any(Users.class));
    }

    @Test
    void changePasswordUsesConfiguredPolicy() {
        Users user = new Users();
        user.setUsername("alice");
        user.setPassword("encoded-old");
        SystemSettings settings = new SystemSettings();
        settings.setMdpLongueurMinimale(10);
        settings.setMdpExigeMajuscule(true);
        settings.setMdpExigeChiffre(true);
        settings.setMdpExigeCaractereSpecial(true);
        when(userRepository.findByUsername("alice")).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches("old", "encoded-old")).thenReturn(true);
        when(systemSettingsService.getSettings()).thenReturn(settings);

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> authService.changePassword("alice", new ChangePasswordRequest("old", "weak")));
        verify(userRepository, never()).save(any(Users.class));
    }

    @Test
    void getProfileReturnsUserDetails() {
        Users user = new Users();
        user.setId(1L);
        user.setUsername("alice");
        user.setFirstName("Alice");
        user.setLastName("Martin");
        user.setEmail("alice@test.local");
        user.setPhone("0600000000");
        user.setAvatar("data:image/png;base64,sample");
        Roles role = new Roles();
        role.setNameRole("ADMIN");
        user.setRoles(role);

        when(userRepository.findByUsername("alice")).thenReturn(java.util.Optional.of(user));

        UserProfileResponse profile = authService.getProfile("alice");

        assertEquals("alice", profile.username());
        assertEquals("Alice", profile.firstName());
        assertEquals("Martin", profile.lastName());
        assertEquals("alice@test.local", profile.email());
        assertEquals("0600000000", profile.phone());
        assertEquals("data:image/png;base64,sample", profile.avatar());
        assertEquals("ADMIN", profile.role());
    }

    @Test
    void updateProfileUpdatesFieldsAndSaves() {
        Users user = new Users();
        user.setId(1L);
        user.setUsername("alice");
        user.setFirstName("Alice");
        user.setLastName("Martin");
        user.setEmail("alice@test.local");
        user.setPhone("0600000000");
        Roles role = new Roles();
        role.setNameRole("USER");
        user.setRoles(role);

        when(userRepository.findByUsername("alice")).thenReturn(java.util.Optional.of(user));
        when(userRepository.existsByEmail("alice.new@test.local")).thenReturn(false);

        UpdateProfileRequest request = new UpdateProfileRequest("Alicia", "Dupont", "alice.new@test.local", "0700000000", "data:image/png;base64,newavatar");
        UserProfileResponse updated = authService.updateProfile("alice", request);

        assertEquals("Alicia", updated.firstName());
        assertEquals("Dupont", updated.lastName());
        assertEquals("alice.new@test.local", updated.email());
        assertEquals("0700000000", updated.phone());
        assertEquals("data:image/png;base64,newavatar", updated.avatar());
        verify(userRepository).save(user);
    }
}
