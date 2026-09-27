package com.tutorcraft.core.shared.domain;

import java.util.Map;

/** Превышен лимит запросов. */
public class RateLimitedException extends DomainException {

    public RateLimitedException(String code, String defaultMessage) {
        super(code, defaultMessage, Map.of());
    }

    public RateLimitedException(String code, String defaultMessage, Map<String, Object> args) {
        super(code, defaultMessage, args);
    }
}
