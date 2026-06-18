package com.vactis.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

@Getter
public class AuthException extends RuntimeException {

    private final AuthErrorCode code;
    private final HttpStatus status;
    private final Integer remainingAttempts;
    private final Integer maxAttempts;
    private final LocalDateTime lockEndTime;
    private final Integer lockMinutes;

    private AuthException(
            String message,
            AuthErrorCode code,
            HttpStatus status,
            Integer remainingAttempts,
            Integer maxAttempts,
            LocalDateTime lockEndTime,
            Integer lockMinutes) {
        super(message);
        this.code = code;
        this.status = status;
        this.remainingAttempts = remainingAttempts;
        this.maxAttempts = maxAttempts;
        this.lockEndTime = lockEndTime;
        this.lockMinutes = lockMinutes;
    }

    public static AuthException badCredentials(Integer remainingAttempts, Integer maxAttempts) {
        String message = "Identifiants invalides.";
        if (remainingAttempts != null && maxAttempts != null && remainingAttempts > 0) {
            message = String.format(
                    "Identifiants invalides. Il vous reste %d tentative(s) sur %d.",
                    remainingAttempts, maxAttempts);
        }
        return new AuthException(
                message,
                AuthErrorCode.BAD_CREDENTIALS,
                HttpStatus.UNAUTHORIZED,
                remainingAttempts,
                maxAttempts,
                null,
                null);
    }

    public static AuthException accountLocked(LocalDateTime lockEndTime, Integer lockMinutes, int maxAttempts) {
        String message = lockMinutes != null
                ? String.format("Compte suspendu pour %d minute(s).", lockMinutes)
                : "Compte suspendu.";
        if (lockEndTime != null) {
            message = message + " Fin : " + lockEndTime + ".";
        }
        return new AuthException(
                message,
                AuthErrorCode.ACCOUNT_LOCKED,
                HttpStatus.LOCKED,
                0,
                maxAttempts,
                lockEndTime,
                lockMinutes);
    }

    public static AuthException accountDisabled() {
        return new AuthException(
                "Compte désactivé. Contactez un administrateur.",
                AuthErrorCode.ACCOUNT_DISABLED,
                HttpStatus.FORBIDDEN,
                null,
                null,
                null,
                null);
    }

    public static AuthException usernameTaken() {
        return new AuthException(
                "Ce nom d'utilisateur est déjà utilisé",
                AuthErrorCode.USERNAME_TAKEN,
                HttpStatus.CONFLICT,
                null,
                null,
                null,
                null);
    }

    public static AuthException emailTaken() {
        return new AuthException(
                "Cet email est déjà utilisé",
                AuthErrorCode.EMAIL_TAKEN,
                HttpStatus.CONFLICT,
                null,
                null,
                null,
                null);
    }
}
