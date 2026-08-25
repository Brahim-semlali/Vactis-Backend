package com.vactis.service.auth;

import com.vactis.model.auth.Users;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private JwtService jwtService;
    private Users user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        ReflectionTestUtils.setField(jwtService, "expiration", 3600000L);
        user = new Users();
        user.setUsername("alice");
    }

    @Test
    void generatedTokenIsValidForItsUser() {
        String token = jwtService.generateToken(user);

        assertTrue(jwtService.isTokenValid(token, user));
    }

    @Test
    void tokenIsInvalidForAnotherUser() {
        String token = jwtService.generateToken(user);
        Users other = new Users();
        other.setUsername("bob");

        assertFalse(jwtService.isTokenValid(token, other));
    }
}
