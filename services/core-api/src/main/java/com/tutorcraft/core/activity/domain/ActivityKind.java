package com.tutorcraft.core.activity.domain;

import java.util.Arrays;
import java.util.Optional;

/** Вид записи журнала активности. */
public enum ActivityKind {
    /** HTTP-запрос к API (любое действие пользователя, в том числе отклонённое). */
    REQUEST("request"),
    /** Переход на страницу интерфейса (сообщает веб-клиент). */
    PAGE_VIEW("page_view"),
    /** Ошибка в браузере: исключение JS, падение экрана, некорректный ответ API. */
    CLIENT_ERROR("client_error");

    private final String key;

    ActivityKind(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<ActivityKind> fromKey(String key) {
        return Arrays.stream(values()).filter(kind -> kind.key.equals(key)).findFirst();
    }
}
