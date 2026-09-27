package com.tutorcraft.core.shared.api;

import com.tutorcraft.core.shared.domain.FieldViolation;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/** Сборка ProblemDetail: локализованный title по коду ошибки, traceId, errors[] (API-03). */
@Component
public class ProblemFactory {

    private static final String TYPE_PREFIX = "https://tutorcraft.dev/problems/";

    private final MessageSource messages;
    private final Tracer tracer;

    public ProblemFactory(MessageSource messages, Tracer tracer) {
        this.messages = messages;
        this.tracer = tracer;
    }

    public ProblemDetail create(HttpStatus status, String code, Map<String, Object> args,
                                List<FieldViolation> violations, Locale locale) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create(TYPE_PREFIX + code));
        problem.setTitle(messages.getMessage(code, args.values().toArray(), status.getReasonPhrase(), locale));
        problem.setProperty("code", code);
        problem.setProperty("traceId", currentTraceId());
        if (!violations.isEmpty()) {
            problem.setProperty("errors", violations);
        }
        if (!args.isEmpty()) {
            problem.setProperty("args", args);
        }
        return problem;
    }

    private String currentTraceId() {
        Span span = tracer.currentSpan();
        return span == null ? null : span.context().traceId();
    }
}
