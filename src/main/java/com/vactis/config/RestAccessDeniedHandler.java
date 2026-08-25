package com.vactis.config;

import com.vactis.dto.common.ErrorResponse;
import com.vactis.exception.AuthErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        String username = request.getUserPrincipal() == null ? "anonymous" : request.getUserPrincipal().getName();
        log.warn("[SECURITY] Accès refusé | method={} | path={} | user={} | authorities={} | reason={}",
            request.getMethod(), request.getServletPath(), username,
            request.getUserPrincipal() instanceof org.springframework.security.core.Authentication authentication
                ? authentication.getAuthorities()
                : "none",
            accessDeniedException.getMessage());
        SecurityJsonWriter.write(response, HttpServletResponse.SC_FORBIDDEN, new ErrorResponse(
                AuthErrorCode.ACCESS_DENIED,
                "Accès refusé à cette ressource",
                null,
                null,
                null,
                null,
                null));
    }
}
