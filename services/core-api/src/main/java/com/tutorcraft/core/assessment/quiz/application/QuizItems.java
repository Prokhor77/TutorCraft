package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.assessment.quiz.domain.QuizSettings;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.courses.ItemType;
import com.tutorcraft.core.progress.LearnerAccess;
import com.tutorcraft.core.shared.domain.ForbiddenException;
import com.tutorcraft.core.shared.domain.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Загрузка элемента-теста и проверка доступа студента к нему (видимость + условия, FR-PROG-02). */
@Component
public class QuizItems {

    private final CoursesApi courses;
    private final AccessService access;
    private final LearnerAccess learnerAccess;

    public QuizItems(CoursesApi courses, AccessService access, LearnerAccess learnerAccess) {
        this.courses = courses;
        this.access = access;
        this.learnerAccess = learnerAccess;
    }

    /** @throws NotFoundException элемента нет в tenant или это не тест */
    public QuizItem require(UUID tenantId, UUID itemId) {
        ItemRef item = courses.requireItem(tenantId, itemId);
        if (item.type() != ItemType.QUIZ) {
            throw new NotFoundException(QuizErrors.NOT_FOUND, "Quiz not found");
        }
        return new QuizItem(item, QuizSettings.parse(item.settings()));
    }

    /** Студенту — только открытый элемент; персонал со скрытым просмотром проходит всегда. */
    public void requireLearnerAccess(UUID tenantId, UUID userId, ItemRef item) {
        if (access.can(Permission.COURSE_VIEW_HIDDEN, AccessContext.course(item.courseId()))) {
            return;
        }
        LearnerAccess.Status status = learnerAccess.statusOf(tenantId, userId, item);
        if (status == LearnerAccess.Status.HIDDEN) {
            throw new NotFoundException(QuizErrors.ITEM_NOT_FOUND, "Item not found");
        }
        if (status == LearnerAccess.Status.LOCKED) {
            throw new ForbiddenException(QuizErrors.UNAVAILABLE, "Quiz is not available yet");
        }
    }

    public record QuizItem(ItemRef item, QuizSettings settings) {

        public UUID id() {
            return item.id();
        }

        public UUID courseId() {
            return item.courseId();
        }

        public AccessContext context() {
            return AccessContext.course(item.courseId());
        }
    }
}
