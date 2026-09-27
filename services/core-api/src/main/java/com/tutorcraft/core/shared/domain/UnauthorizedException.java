package com.tutorcraft.core.shared.domain;

import java.util.Map;

/** Требуется аутентификация или она неуспешна. */
public class UnauthorizedException extends DomainException {

    public UnauthorizedException(String code, String defaultMessage) {
        super(code, defaultMessage, Map.of());
    }

    public UnauthorizedException(String code, String defaultMessage, Map<String, Object> args) {
        super(code, defaultMessage, args);
    }
}
