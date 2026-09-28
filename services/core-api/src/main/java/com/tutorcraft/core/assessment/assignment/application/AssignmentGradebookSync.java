package com.tutorcraft.core.assessment.assignment.application;

import com.tutorcraft.core.assessment.assignment.domain.AssignmentSettings;
import com.tutorcraft.core.assessment.assignment.domain.AssignmentSettingsParser;
import com.tutorcraft.core.courses.CourseEvents.ChangeKind;
import com.tutorcraft.core.courses.CourseEvents.ItemChanged;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.courses.ItemType;
import com.tutorcraft.core.gradebook.GradebookApi;
import com.tutorcraft.core.shared.domain.ValidationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Синхронизация столбца журнала с заданием: создание/изменение/восстановление → ensure, удаление → remove. */
@Component
class AssignmentGradebookSync {

    private static final Logger log = LoggerFactory.getLogger(AssignmentGradebookSync.class);

    private final CoursesApi courses;
    private final GradebookApi gradebook;

    AssignmentGradebookSync(CoursesApi courses, GradebookApi gradebook) {
        this.courses = courses;
        this.gradebook = gradebook;
    }

    /** События courses публикуются после записи в MongoDB — изменения журнала фиксируются в собственной транзакции. */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onItemChanged(ItemChanged event) {
        if (event.type() != ItemType.ASSIGNMENT) {
            return;
        }
        if (event.kind() == ChangeKind.DELETED) {
            gradebook.removeGradeItem(event.tenantId(), event.itemId());
            return;
        }
        ItemRef item = courses.findItems(event.tenantId(), List.of(event.itemId())).get(event.itemId());
        if (item == null) {
            return;
        }
        ensureGradeItem(item);
    }

    private void ensureGradeItem(ItemRef item) {
        try {
            AssignmentSettings settings = AssignmentSettingsParser.parse(item.settings());
            gradebook.ensureGradeItem(item.tenantId(), item.courseId(), item.id(), item.title(), settings.maxScore(),
                    settings.gradeCategoryId());
        } catch (ValidationException e) {
            log.warn("Assignment {} has invalid settings, grade item not synchronized", item.id());
        }
    }
}
