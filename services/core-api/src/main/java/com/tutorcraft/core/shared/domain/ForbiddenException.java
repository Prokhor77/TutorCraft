package com.tutorcraft.core.shared.domain;

import java.util.Map;

/** Недостаточно прав. */
public class ForbiddenException extends DomainException {

    public ForbiddenException(String code, String defaultMessage) {
        super(code, defaultMessage, Map.of());
    }

    public ForbiddenException(String code, String defaultMessage, Map<String, Object> args) {
        super(code, defaultMessage, args);
    }
}
