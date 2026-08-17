package com.vactis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vactis.dto.common.ErrorResponse;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;

final class SecurityJsonWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SecurityJsonWriter() {
    }

    static void write(HttpServletResponse response, int status, ErrorResponse body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        MAPPER.writeValue(response.getWriter(), body);
    }
}
