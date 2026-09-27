package com.tutorcraft.core.shared.api;

import com.tutorcraft.core.shared.domain.BusinessRuleException;
import com.tutorcraft.core.shared.domain.ConflictException;
import com.tutorcraft.core.shared.domain.DomainException;
import com.tutorcraft.core.shared.domain.FieldViolation;
import com.tutorcraft.core.shared.domain.ForbiddenException;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.domain.RateLimitedException;
import com.tutorcraft.core.shared.domain.UnauthorizedException;
import com.tutorcraft.core.shared.domain.ValidationException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Единый формат ошибок (API-03). Стектрейсы и SQL клиенту не отдаются. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ProblemFactory problems;

    public GlobalExceptionHandler(ProblemFactory problems) {
        this.problems = problems;
    }

    @ExceptionHandler(ValidationException.class)
    ProblemDetail handleValidation(ValidationException ex, Locale locale) {
        return problems.create(HttpStatus.BAD_REQUEST, ex.code(), ex.args(), ex.violations(), locale);
    }

    @ExceptionHandler(DomainException.class)
    ProblemDetail handleDomain(DomainException ex, Locale locale) {
        HttpStatus status = statusOf(ex);
        log.debug("Domain error {} -> {}", ex.code(), status.value());
        return problems.create(status, ex.code(), ex.args(), List.of(), locale);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleBeanValidation(MethodArgumentNotValidException ex, Locale locale) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), String.valueOf(error.getCode()), error.getDefaultMessage()))
                .toList();
        return problems.create(HttpStatus.BAD_REQUEST, "validation.failed", Map.of(), violations, locale);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail handleMalformed(Exception ex, Locale locale) {
        log.debug("Malformed request: {}", ex.getClass().getSimpleName());
        return problems.create(HttpStatus.BAD_REQUEST, "request.malformed", Map.of(), List.of(), locale);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ProblemDetail handleMissingHeader(MissingRequestHeaderException ex, Locale locale) {
        List<FieldViolation> violations = List.of(new FieldViolation(ex.getHeaderName(), "required", "Header is required"));
        return problems.create(HttpStatus.BAD_REQUEST, "validation.failed", Map.of(), violations, locale);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail handleNoRoute(NoResourceFoundException ex, Locale locale) {
        return problems.create(HttpStatus.NOT_FOUND, "resource.not_found", Map.of(), List.of(), locale);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex, Locale locale) {
        log.error("Unhandled error", ex);
        return problems.create(HttpStatus.INTERNAL_SERVER_ERROR, "internal.error", Map.of(), List.of(), locale);
    }

    private static HttpStatus statusOf(DomainException ex) {
        return switch (ex) {
            case NotFoundException e -> HttpStatus.NOT_FOUND;
            case ForbiddenException e -> HttpStatus.FORBIDDEN;
            case ConflictException e -> ex.code().equals(IfMatch.VERSION_CONFLICT_CODE) ? HttpStatus.PRECONDITION_FAILED : HttpStatus.CONFLICT;
            case BusinessRuleException e -> HttpStatus.UNPROCESSABLE_ENTITY;
            case UnauthorizedException e -> HttpStatus.UNAUTHORIZED;
            case RateLimitedException e -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
