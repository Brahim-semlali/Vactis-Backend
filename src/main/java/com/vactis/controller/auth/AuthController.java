package com.vactis.controller.auth;

import com.vactis.dto.auth.AccountStatusResponse;
import com.vactis.dto.auth.AuthResponse;
import com.vactis.dto.auth.LoginRequest;
import com.vactis.dto.auth.RegisterRequest;
import com.vactis.service.auth.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        log.info("[AUTH-API] POST /register | username={} | email={}",
                request.username(), request.email());
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        log.info("[AUTH-API] POST /login | username={}", request.username());
        return authService.login(request);
    }

    @GetMapping("/account-status")
    public AccountStatusResponse accountStatus(@RequestParam String username) {
        log.info("[AUTH-API] GET /account-status | username={}", username);
        return authService.getAccountStatus(username);
    }
}
