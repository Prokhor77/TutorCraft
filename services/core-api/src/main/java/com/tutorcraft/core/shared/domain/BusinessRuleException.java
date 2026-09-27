package com.tutorcraft.core.shared.domain;

import java.util.Map;

/** Нарушено бизнес-правило (HTTP 422). */
public class BusinessRuleException extends DomainException {

    public BusinessRuleException(String code, String defaultMessage) {
        super(code, defaultMessage, Map.of());
    }

    public BusinessRuleException(String code, String defaultMessage, Map<String, Object> args) {
        super(code, defaultMessage, args);
    }
}
