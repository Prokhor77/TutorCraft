package com.tutorcraft.core.assessment.assignment.application;

import com.tutorcraft.core.assessment.assignment.domain.AssignmentSettings;
import com.tutorcraft.core.courses.ItemRef;
import java.util.UUID;

/** Элемент курса типа «задание» с разобранными настройками. */
public record AssignmentItem(ItemRef item, AssignmentSettings settings) {

    public UUID tenantId() {
        return item.tenantId();
    }

    public UUID courseId() {
        return item.courseId();
    }

    public UUID itemId() {
        return item.id();
    }
}
