package com.tutorcraft.core.shared.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/** Запись ошибок в формате RFC 9457 вне MVC (фильтры, security entry points). */
@Component
public class ProblemWriter {

    private final ObjectMapper objectMapper;
    private final ProblemFactory problemFactory;

    public ProblemWriter(ObjectMapper objectMapper, ProblemFactory problemFactory) {
        this.objectMapper = objectMapper;
        this.problemFactory = problemFactory;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code)
            throws IOException {
        ProblemDetail problem = problemFactory.create(status, code, java.util.Map.of(), List.of(), request.getLocale());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
