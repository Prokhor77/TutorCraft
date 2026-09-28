package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.assessment.quiz.domain.QuizSettings;
import com.tutorcraft.core.assessment.quiz.domain.ReviewTiming;
import com.tutorcraft.core.courses.CourseEvents.ChangeKind;
import com.tutorcraft.core.courses.CourseEvents.ItemChanged;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.courses.ItemType;
import com.tutorcraft.core.gradebook.GradebookApi;
import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Синхронизация теста с журналом: элемент оценивания (maxScore, категория) и расписание публикации оценок
 * при {@code review.whenScore = after_close}. Удаление теста убирает элемент оценивания.
 */
@Component
class QuizItemChangeListener {

    private final CoursesApi courses;
    private final GradebookApi gradebook;
    private final GradeReleaseRepository releases;

    QuizItemChangeListener(CoursesApi courses, GradebookApi gradebook, GradeReleaseRepository releases) {
        this.courses = courses;
        this.gradebook = gradebook;
        this.releases = releases;
    }

    @EventListener
    @Transactional
    public void onItemChanged(ItemChanged event) {
        if (event.type() != ItemType.QUIZ) {
            return;
        }
        if (event.kind() == ChangeKind.DELETED) {
            gradebook.removeGradeItem(event.tenantId(), event.itemId());
            releases.cancel(event.tenantId(), event.itemId());
            return;
        }
        ItemRef item = courses.findItems(event.tenantId(), List.of(event.itemId())).get(event.itemId());
        if (item != null) {
            sync(item);
        }
    }

    private void sync(ItemRef item) {
        QuizSettings settings = QuizSettings.parse(item.settings());
        gradebook.ensureGradeItem(item.tenantId(), item.courseId(), item.id(), item.title(), settings.maxScore(),
                settings.gradeCategoryId());
        boolean deferred = settings.review().whenScore() == ReviewTiming.AFTER_CLOSE && settings.closeAt() != null;
        if (deferred) {
            releases.schedule(item.tenantId(), item.courseId(), item.id(), settings.closeAt());
        } else {
            releases.cancel(item.tenantId(), item.id());
        }
    }
}
