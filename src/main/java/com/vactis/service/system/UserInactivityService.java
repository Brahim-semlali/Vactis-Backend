package com.vactis.service.system;

import com.vactis.model.auth.Users;
import com.vactis.repository.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserInactivityService {
    private final UserRepository userRepository;
    private final SystemSettingsService systemSettingsService;

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void deactivateInactiveUsers() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(systemSettingsService.getSettings().getDureeInactiviteJours());
        List<Users> users = userRepository.findByEnabledTrueAndAccountLockedFalse();
        List<Users> inactiveUsers = users.stream()
                .filter(user -> user.getLastLoginAt() != null && user.getLastLoginAt().isBefore(threshold))
            .peek(user -> user.setEnabled(false))
            .collect(Collectors.toList());
        userRepository.saveAll(inactiveUsers);
        log.info("Vérification d'inactivité terminée | seuil={} | utilisateurs examinés={}", threshold, users.size());
    }
}