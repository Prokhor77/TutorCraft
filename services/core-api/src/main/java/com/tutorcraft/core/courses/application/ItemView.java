package com.tutorcraft.core.courses.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tutorcraft.core.courses.Availability;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Item / ItemDetail (контракт §5). permissions присутствует только в ItemDetail (GET /items/{id}).
 * Для учащегося settings — {@code ActivityType.learnerView}; у видео добавлены videoStatus и hlsUrl.
 */
public record ItemView(UUID id, String type, String title, int position, String visibility, Instant publishAt,
                       Instant dueAt, Availability availability, String completion, String status, long version,
                       UUID moduleId, UUID courseId, Map<String, Object> settings, Map<String, Object> content,
                       Map<String, Object> completionRule, Map<String, Object> conditions,
                       @JsonInclude(JsonInclude.Include.NON_NULL) List<String> permissions) {
}
