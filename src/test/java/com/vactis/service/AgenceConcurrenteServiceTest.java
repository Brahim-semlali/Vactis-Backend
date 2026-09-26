package com.vactis.service;

import com.vactis.dto.concurrence.AgenceConcurrenteRequest;
import com.vactis.dto.concurrence.AgenceConcurrenteResponse;
import com.vactis.model.auth.Users;
import com.vactis.model.concurrence.AgenceConcurrente;
import com.vactis.repository.AgenceConcurrenteRepository;
import com.vactis.repository.auth.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgenceConcurrenteServiceTest {

    @Mock
    private AgenceConcurrenteRepository repository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private AgenceConcurrenteService service;

    @BeforeEach
    void setUpSecurity() {
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void getAllAgencesReturnsSortedResponses() {
        AgenceConcurrente a1 = AgenceConcurrente.builder()
                .id(1L)
                .nom("Labo Alpha")
                .latitude(31.63)
                .longitude(-7.99)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(repository.findAllByOrderByNomAsc()).thenReturn(List.of(a1));

        List<AgenceConcurrenteResponse> result = service.getAllAgences();
        assertEquals(1, result.size());
        assertEquals("Labo Alpha", result.get(0).nom());
    }

    @Test
    void createAgenceValidatesCoordinatesAndAttachesUser() {
        Users admin = new Users();
        admin.setId(10L);
        admin.setUsername("admin_test");

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("admin_test");
        when(userRepository.findByUsername("admin_test")).thenReturn(Optional.of(admin));
        when(repository.save(any(AgenceConcurrente.class))).thenAnswer(i -> {
            AgenceConcurrente toSave = i.getArgument(0);
            toSave.setId(100L);
            return toSave;
        });

        AgenceConcurrenteRequest request = new AgenceConcurrenteRequest(
                "Labo Central",
                "Réseau Bio",
                31.6295,
                -7.9811,
                "Boulevard Mohamed VI",
                "Grand concurrent"
        );

        AgenceConcurrenteResponse response = service.createAgence(request);
        assertNotNull(response);
        assertEquals(100L, response.id());
        assertEquals("Labo Central", response.nom());
        assertEquals("admin_test", response.createdBy());
    }

    @Test
    void createAgenceRejectsInvalidLatitude() {
        AgenceConcurrenteRequest request = new AgenceConcurrenteRequest(
                "Labo Hors Limites",
                null,
                95.0, // Invalid latitude (> 90)
                -7.9811,
                null,
                null
        );

        assertThrows(ResponseStatusException.class, () -> service.createAgence(request));
        verify(repository, never()).save(any());
    }

    @Test
    void createAgenceRejectsBlankNom() {
        AgenceConcurrenteRequest request = new AgenceConcurrenteRequest(
                "   ",
                null,
                31.62,
                -7.98,
                null,
                null
        );

        assertThrows(ResponseStatusException.class, () -> service.createAgence(request));
        verify(repository, never()).save(any());
    }

    @Test
    void deleteAgenceDeletesExisting() {
        when(repository.existsById(1L)).thenReturn(true);

        service.deleteAgence(1L);
        verify(repository).deleteById(1L);
    }

    @Test
    void deleteAgenceThrowsIfNotFound() {
        when(repository.existsById(999L)).thenReturn(false);

        assertThrows(ResponseStatusException.class, () -> service.deleteAgence(999L));
        verify(repository, never()).deleteById(any());
    }
}
