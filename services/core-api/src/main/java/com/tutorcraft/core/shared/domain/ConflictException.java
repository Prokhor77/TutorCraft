package com.tutorcraft.core.shared.domain;

import java.util.Map;

/** Конфликт состояния (версия, дубликат). */
public class ConflictException extends DomainException {

    public ConflictException(String code, String defaultMessage) {
        super(code, defaultMessage, Map.of());
    }

    public ConflictException(String code, String defaultMessage, Map<String, Object> args) {
        super(code, defaultMessage, args);
    }
}
