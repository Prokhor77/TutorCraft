package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.courses.Availability;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** CourseOutline (контракт §5): модули верхнего уровня с подмодулями и элементами. */
public record OutlineView(UUID courseId, List<ModuleView> modules) {

    /** OutlineModule; также ответ на создание/правку модуля. */
    public record ModuleView(UUID id, UUID parentId, String title, int position, String visibility, Instant publishAt,
                             Availability availability, List<OutlineItemView> items, List<ModuleView> children,
                             long version) {
    }

    /** OutlineItem. completion/status — только для учащегося (у преподавателя null). */
    public record OutlineItemView(UUID id, String type, String title, int position, String visibility, Instant publishAt,
                                  Instant dueAt, Availability availability, String completion, String status, long version) {
    }
}
