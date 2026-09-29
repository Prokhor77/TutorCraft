package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.courses.domain.CourseCompletionRule;
import com.tutorcraft.core.courses.domain.SelfEnrolSettings;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Course (контракт §5). selfEnrol.code виден только имеющим enrollment.manage. */
public record CourseView(UUID id, String title, String shortName, String slug, UUID categoryId, Map<String, Object> description,
                         UUID coverFileId, String coverUrl, Instant startsAt, Instant endsAt, String visibility,
                         Instant publishAt, SelfEnrolSettings selfEnrol, CourseCompletionRule completionRule,
                         String groupMode, String myRole, List<String> permissions, long version) {
}
