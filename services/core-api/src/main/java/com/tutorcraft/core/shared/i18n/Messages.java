package com.tutorcraft.core.shared.i18n;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/** Локализованные тексты для application-слоя (причины недоступности, уведомления). */
@Component
public class Messages {

    private final MessageSource source;

    public Messages(MessageSource source) {
        this.source = source;
    }

    public String get(String code, Object... args) {
        return get(LocaleContextHolder.getLocale(), code, args);
    }

    public String get(Locale locale, String code, Object... args) {
        return source.getMessage(code, args, code, locale);
    }
}
