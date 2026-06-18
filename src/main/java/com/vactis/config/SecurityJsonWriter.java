package com.vactis.config;

import com.vactis.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

final class SecurityJsonWriter {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private SecurityJsonWriter() {
    }

    static void write(HttpServletResponse response, int status, ErrorResponse body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        MAPPER.writeValue(response.getWriter(), body);
    }
}
