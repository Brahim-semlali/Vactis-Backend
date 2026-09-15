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

import java.util.Optional;

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
        UserAdminRequest request = new UserAdminRequest("alice", "secret1", "Alice", "Martin", "alice@test.local", null, null, null);
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
    void createUserPersistsAvatarWhenProvided() {
        UserAdminRequest request = new UserAdminRequest(
                "alice",
                "secret1",
                "Alice",
                "Martin",
                "alice@test.local",
                null,
                null,
                "data:image/png;base64,avatar"
        );
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@test.local")).thenReturn(false);
        when(passwordEncoder.encode("secret1")).thenReturn("encoded");
        when(userRepository.save(any(Users.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Users created = userService.createUser(request);

        assertEquals("data:image/png;base64,avatar", created.getAvatar());
    }

    @Test
    void createUserRejectsDuplicateUsername() {
        UserAdminRequest request = new UserAdminRequest("alice", "secret1", "Alice", "Martin", "alice@test.local", null, true, null);
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> userService.createUser(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUserPersistsNewAvatar() {
        Users existing = new Users();
        existing.setId(1L);
        existing.setUsername("alice");
        existing.setFirstName("Alice");
        existing.setLastName("Martin");
        existing.setEmail("alice@test.local");
        existing.setPhone("0600000000");
        existing.setEnabled(true);
        existing.setAvatar("data:image/png;base64,old-avatar");

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmail("alice.new@test.local")).thenReturn(false);
        when(userRepository.save(existing)).thenReturn(existing);

        UserAdminRequest request = new UserAdminRequest(
                "alice",
                "",
                "Alicia",
                "Dupont",
                "alice.new@test.local",
                "0700000000",
                true,
                "data:image/png;base64,new-avatar"
        );

        Users updated = userService.updateUser(1L, request);

        assertEquals("Alicia", updated.getFirstName());
        assertEquals("Dupont", updated.getLastName());
        assertEquals("alice.new@test.local", updated.getEmail());
        assertEquals("data:image/png;base64,new-avatar", updated.getAvatar());
        verify(userRepository).save(existing);
    }

    @Test
    void updateUserKeepsExistingAvatarWhenRequestOmitsIt() {
        Users existing = new Users();
        existing.setId(1L);
        existing.setUsername("alice");
        existing.setFirstName("Alice");
        existing.setLastName("Martin");
        existing.setEmail("alice@test.local");
        existing.setPhone("0600000000");
        existing.setEnabled(true);
        existing.setAvatar("data:image/png;base64,existing-avatar");

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        UserAdminRequest request = new UserAdminRequest(
                "alice",
                "",
                "Alice",
                "Martin",
                "alice@test.local",
                "0600000000",
                true,
                null
        );

        Users updated = userService.updateUser(1L, request);

        assertEquals("data:image/png;base64,existing-avatar", updated.getAvatar());
    }
}
