package com.tutorcraft.core.shared.domain;

import java.util.List;
import java.util.Map;

/** Ошибка валидации входных данных (HTTP 400) со списком нарушений по полям. */
public class ValidationException extends DomainException {

    private static final String CODE = "validation.failed";

    private final transient List<FieldViolation> violations;

    public ValidationException(List<FieldViolation> violations) {
        super(CODE, "Validation failed", Map.of());
        this.violations = List.copyOf(violations);
    }

    public static ValidationException single(String field, String code, String message) {
        return new ValidationException(List.of(new FieldViolation(field, code, message)));
    }

    public List<FieldViolation> violations() {
        return violations;
    }
}
