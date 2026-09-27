package com.tutorcraft.core.shared.domain;

import java.util.Map;

/**
 * Базовое исключение доменного/прикладного уровня. {@code code} — машинный код ошибки
 * (например {@code course.not_found}); текст для клиента берётся из локализации по коду.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;
    private final transient Map<String, Object> args;

    protected DomainException(String code, String defaultMessage, Map<String, Object> args) {
        super(defaultMessage);
        this.code = code;
        this.args = args == null ? Map.of() : Map.copyOf(args);
    }

    public String code() {
        return code;
    }

    public Map<String, Object> args() {
        return args;
    }
}
