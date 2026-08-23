package com.vactis.service.auth;

import com.vactis.dto.auth.UserAdminRequest;
import com.vactis.model.auth.Users;
import com.vactis.model.Roles.Roles;
import com.vactis.repository.RoleRepository;
import com.vactis.repository.auth.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Objects;
import jakarta.persistence.EntityNotFoundException;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {
        log.debug("Chargement de l'utilisateur depuis la BDD : {}", username);
        return userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("Utilisateur non trouvé en BDD : {}", username);
                    return new UsernameNotFoundException(
                            "Utilisateur non trouvé : " + username);
                });
    }

    public List<Users> getAllUsers(){
        return userRepository.findAll();
    }

    public Users createUser(UserAdminRequest request) {
        if (request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le mot de passe est obligatoire");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nom d'utilisateur est déjà utilisé");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'email est déjà utilisé");
        }

        Users user = new Users();
        applyUserData(user, request);
        user.setPassword(passwordEncoder.encode(request.password()));
        return userRepository.save(user);
    }

    public Users updateUser(Long userId, UserAdminRequest request) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable : " + userId));
        userRepository.findByUsername(request.username()).ifPresent(existing -> {
            if (!existing.getId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nom d'utilisateur est déjà utilisé");
            }
        });
        userRepository.findById(userId).ifPresent(existing -> {
            if (!Objects.equals(existing.getEmail(), request.email()) && userRepository.existsByEmail(request.email())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'email est déjà utilisé");
            }
        });
        applyUserData(user, request);
        if (request.password() != null && !request.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }
        return userRepository.save(user);
    }

    public void deleteUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable : " + userId);
        }
        userRepository.deleteById(userId);
    }

    private void applyUserData(Users user, UserAdminRequest request) {
        user.setUsername(request.username());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setEnabled(request.enabled() == null || request.enabled());
    }

    public void assignRole(Long userId, Long roleId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Utilisateur introuvable : " + userId));
        Roles role = roleRepository.findById(roleId)
                .orElseThrow(() -> new EntityNotFoundException("Rôle introuvable : " + roleId));

        user.setRoles(role);
        userRepository.save(user);
    }

}
