package com.vactis.service.system;

import com.vactis.model.auth.Users;
import com.vactis.model.system.ConnexionLog;
import com.vactis.repository.system.ConnexionLogRepository;
import com.vactis.model.system.SystemSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnexionLogServiceTest {
    @Mock private ConnexionLogRepository repository;
    @Mock private SystemSettingsService settingsService;
    @InjectMocks private ConnexionLogService service;

    @Test
    void doesNotPersistWhenJournalIsDisabled() {
        SystemSettings settings = new SystemSettings();
        settings.setJournalConnexionActif(false);
        when(settingsService.getSettings()).thenReturn(settings);

        service.logAttempt(new Users(), false, "127.0.0.1");

        verify(repository, never()).save(any(ConnexionLog.class));
    }

    @Test
    void persistsSuccessfulAttemptWhenJournalIsEnabled() {
        SystemSettings settings = new SystemSettings();
        settings.setJournalConnexionActif(true);
        when(settingsService.getSettings()).thenReturn(settings);

        service.logAttempt(new Users(), true, "127.0.0.1");

        verify(repository).save(any(ConnexionLog.class));
    }
}