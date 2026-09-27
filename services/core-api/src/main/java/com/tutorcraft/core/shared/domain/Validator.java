package com.tutorcraft.core.shared.domain;

import java.util.ArrayList;
import java.util.List;

/** Накопитель нарушений для доменной валидации без вложенных if. */
public final class Validator {

    private final List<FieldViolation> violations = new ArrayList<>();

    public Validator check(boolean condition, String field, String code, String message) {
        if (!condition) {
            violations.add(new FieldViolation(field, code, message));
        }
        return this;
    }

    public Validator notBlank(String value, String field) {
        return check(value != null && !value.isBlank(), field, "required", "Field is required");
    }

    public Validator maxLength(String value, int max, String field) {
        return check(value == null || value.length() <= max, field, "too_long", "Maximum length is " + max);
    }

    public boolean hasErrors() {
        return !violations.isEmpty();
    }

    public void throwIfInvalid() {
        if (hasErrors()) {
            throw new ValidationException(violations);
        }
    }
}
