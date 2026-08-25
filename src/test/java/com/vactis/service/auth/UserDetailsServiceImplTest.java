package com.vactis.service.auth;

import com.vactis.dto.auth.UserAdminRequest;
import com.vactis.model.auth.Users;
import com.vactis.repository.RoleRepository;
import com.vactis.repository.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private UserDetailsServiceImpl userService;

    @Test
    void createUserEncodesPasswordAndEnablesUserByDefault() {
        UserAdminRequest request = new UserAdminRequest("alice", "secret1", "Alice", "Martin", "alice@test.local", null, null);
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@test.local")).thenReturn(false);
        when(passwordEncoder.encode("secret1")).thenReturn("encoded");
        when(userRepository.save(any(Users.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Users created = userService.createUser(request);

        assertEquals("alice", created.getUsername());
        assertEquals("encoded", created.getPassword());
        assertEquals("Alice", created.getFirstName());
        assertEquals("ACTIF", created.getStatus());
        verify(userRepository).save(any(Users.class));
    }

    @Test
    void createUserRejectsDuplicateUsername() {
        UserAdminRequest request = new UserAdminRequest("alice", "secret1", "Alice", "Martin", "alice@test.local", null, true);
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> userService.createUser(request));
        verify(userRepository, never()).save(any());
    }
}
