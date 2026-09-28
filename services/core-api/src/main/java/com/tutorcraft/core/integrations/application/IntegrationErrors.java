package com.tutorcraft.core.integrations.application;

/** Коды ошибок модуля integrations (тексты — i18n/integrations*.properties). */
public final class IntegrationErrors {

    public static final String TOKEN_NOT_FOUND = "integrations.token_not_found";
    public static final String WEBHOOK_NOT_FOUND = "integrations.webhook_not_found";
    public static final String INVALID_TOKEN = "auth.invalid_token";
    public static final String READ_ONLY_TOKEN = "integrations.token_read_only";
    public static final String URL_PREFIX = "integrations.url_";

    private IntegrationErrors() {
    }
}
