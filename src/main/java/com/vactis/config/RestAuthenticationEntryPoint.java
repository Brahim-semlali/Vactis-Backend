package com.vactis.config;

import com.vactis.dto.ErrorResponse;
import com.vactis.exception.AuthErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("[SECURITY] Non authentifié | path={} | {}", request.getServletPath(), authException.getMessage());
        SecurityJsonWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, new ErrorResponse(
                AuthErrorCode.BAD_CREDENTIALS,
                "Authentification requise",
                null,
                null,
                null,
                null,
                null));
    }
}
