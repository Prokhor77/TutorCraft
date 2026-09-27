package com.tutorcraft.core.shared.domain;

import java.util.Map;

/** Ресурс не найден или недоступен. Для чужого tenant также 404 (AC-1). */
public class NotFoundException extends DomainException {

    public NotFoundException(String code, String defaultMessage) {
        super(code, defaultMessage, Map.of());
    }

    public NotFoundException(String code, String defaultMessage, Map<String, Object> args) {
        super(code, defaultMessage, args);
    }
}
