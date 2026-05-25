package com.vactis.service;

import com.vactis.dto.AuthResponse;
import com.vactis.dto.LoginRequest;
import com.vactis.dto.RegisterRequest;
import com.vactis.model.Role;
import com.vactis.model.Users;
import com.vactis.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) {
        log.info("Inscription de l'utilisateur : {}", request.username());
        Users user = new Users();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER);
        userRepository.save(user);
        log.info("Utilisateur inscrit avec succès : {}", request.username());
        return new AuthResponse(jwtService.generateToken(user));
    }

    public AuthResponse login(LoginRequest request) {
        log.info("Tentative de connexion pour : {}", request.username());
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(), request.password()));
        Users user = userRepository.findByUsername(request.username()).orElseThrow();
        log.info("Connexion réussie pour : {}", request.username());
        return new AuthResponse(jwtService.generateToken(user));
    }
}
