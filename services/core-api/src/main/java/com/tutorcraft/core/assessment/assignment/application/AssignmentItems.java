package com.tutorcraft.core.assessment.assignment.application;

import com.tutorcraft.core.assessment.assignment.domain.AssignmentErrors;
import com.tutorcraft.core.assessment.assignment.domain.AssignmentSettingsParser;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.courses.ItemType;
import com.tutorcraft.core.shared.domain.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Загрузка элемента-задания через CoursesApi (tenant фильтруется там же; удалённое → 404). */
@Component
class AssignmentItems {

    private final CoursesApi courses;

    AssignmentItems(CoursesApi courses) {
        this.courses = courses;
    }

    AssignmentItem require(UUID tenantId, UUID itemId) {
        ItemRef item = courses.requireItem(tenantId, itemId);
        if (item.type() != ItemType.ASSIGNMENT) {
            throw new NotFoundException(AssignmentErrors.NOT_FOUND, "Assignment not found");
        }
        return new AssignmentItem(item, AssignmentSettingsParser.parse(item.settings()));
    }
}
