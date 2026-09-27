package com.tutorcraft.core.courses;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ModuleRef(UUID id, UUID courseId, UUID parentId, String title, int position, Visibility visibility,
                        Instant publishAt, Map<String, Object> conditions) {
}
