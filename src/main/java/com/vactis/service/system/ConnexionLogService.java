package com.vactis.service.system;

import com.vactis.model.auth.Users;
import com.vactis.model.system.ConnexionLog;
import com.vactis.repository.system.ConnexionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ConnexionLogService {
    private final ConnexionLogRepository repository;
    private final SystemSettingsService settingsService;

    @Transactional
    public void logAttempt(Users user, boolean success, String ipAddress) {
        if (!settingsService.getSettings().getJournalConnexionActif()) {
            return;
        }
        ConnexionLog log = new ConnexionLog();
        log.setUserId(user == null ? null : user.getId());
        log.setDateConnexion(LocalDateTime.now());
        log.setAdresseIp(ipAddress);
        log.setSucces(success);
        repository.save(log);
    }

    @Transactional(readOnly = true)
    public Page<ConnexionLog> search(Long userId, LocalDateTime dateDebut, LocalDateTime dateFin, Pageable pageable) {
        if (!settingsService.getSettings().getJournalConnexionActif()) {
            return Page.empty(pageable);
        }
        Specification<ConnexionLog> specification = Specification.where(null);
        if (userId != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("userId"), userId));
        }
        if (dateDebut != null) {
            specification = specification.and((root, query, builder) -> builder.greaterThanOrEqualTo(root.get("dateConnexion"), dateDebut));
        }
        if (dateFin != null) {
            specification = specification.and((root, query, builder) -> builder.lessThanOrEqualTo(root.get("dateConnexion"), dateFin));
        }
        Pageable sortedPageable = PageRequest.of(
            pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "dateConnexion"));
        return repository.findAll(specification, sortedPageable);
    }

    @Transactional
    public void closeLatestLog(Long userId) {
        if (!settingsService.getSettings().getJournalConnexionActif() || userId == null) {
            return;
        }
        repository.findTopByUserIdAndDateDeconnexionIsNullOrderByDateConnexionDesc(userId)
                .ifPresent(log -> {
                    log.setDateDeconnexion(LocalDateTime.now());
                    repository.save(log);
                });
    }
}