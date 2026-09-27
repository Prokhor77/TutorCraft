package com.tutorcraft.core.courses;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Снимок элемента курса (MongoDB, коллекция items) для других модулей.
 * {@code settings} — нормализованные настройки, схема которых принадлежит {@link com.tutorcraft.core.courses.spi.ActivityType}.
 * {@code dueAt/openAt/closeAt} вычисляются ActivityType при записи — для «Моих задач» и календаря.
 * {@code conditions} — JSON-дерево условий доступа (схема — модуль progress).
 */
public record ItemRef(UUID id, UUID tenantId, UUID courseId, UUID moduleId, ItemType type, String title, int position,
                      Visibility visibility, Instant publishAt, Instant dueAt, Instant openAt, Instant closeAt,
                      Map<String, Object> settings, Map<String, Object> completionRule, Map<String, Object> conditions,
                      long version) {

    public <T> T settingsAs(ObjectMapper mapper, Class<T> type) {
        return mapper.convertValue(settings, type);
    }

    /** Режим выполнения: none | manual | auto. */
    @SuppressWarnings("unchecked")
    public String completionMode() {
        Object mode = completionRule == null ? null : completionRule.get("mode");
        return mode == null ? "none" : mode.toString();
    }

    @SuppressWarnings("unchecked")
    public List<String> completionTriggers() {
        Object on = completionRule == null ? null : completionRule.get("on");
        return on instanceof List<?> list ? (List<String>) list : List.of();
    }
}
