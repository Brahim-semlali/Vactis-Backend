package com.vactis.service.auth;

import com.vactis.dto.auth.RegisterRequest;
import com.vactis.model.Roles.Roles;
import com.vactis.model.auth.Users;
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
}
