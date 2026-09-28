package com.tutorcraft.core.courses.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.tutorcraft.core.courses.ItemType;
import com.tutorcraft.core.courses.Visibility;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** FR-COURSE-04: элемент виден студенту только если видимы курс, модуль, родительский модуль и элемент. */
class LearnerVisibilityTest {

    private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID COURSE = UUID.randomUUID();

    private static Course course(Visibility visibility, Instant publishAt) {
        return Course.blank(COURSE, TENANT, UUID.randomUUID(), NOW).toBuilder().title("C").slug("c")
                .visibility(visibility).publishAt(publishAt).build();
    }

    private static CourseModule module(UUID parentId, Visibility visibility, Instant publishAt) {
        return new CourseModule(UUID.randomUUID(), TENANT, COURSE, parentId, "M", 0, visibility, publishAt, null, 0, null);
    }

    private static CourseItem item(CourseModule module, Visibility visibility) {
        return new CourseItem(UUID.randomUUID(), TENANT, COURSE, module.id(), ItemType.PAGE, "I", 0, visibility, null,
                Map.of("kind", "page"), null, null, null, null, 0, null, NOW, NOW);
    }

    @Test
    void publishedChainIsVisible() {
        CourseModule module = module(null, Visibility.PUBLISHED, null);
        assertThat(LearnerVisibility.itemVisible(course(Visibility.PUBLISHED, null), module, null,
                item(module, Visibility.PUBLISHED), NOW)).isTrue();
    }

    @Test
    void hiddenCourseHidesEverything() {
        CourseModule module = module(null, Visibility.PUBLISHED, null);
        assertThat(LearnerVisibility.itemVisible(course(Visibility.HIDDEN, null), module, null,
                item(module, Visibility.PUBLISHED), NOW)).isFalse();
    }

    @Test
    void hiddenModuleHidesItem() {
        CourseModule module = module(null, Visibility.HIDDEN, null);
        assertThat(LearnerVisibility.itemVisible(course(Visibility.PUBLISHED, null), module, null,
                item(module, Visibility.PUBLISHED), NOW)).isFalse();
    }

    @Test
    void hiddenParentModuleHidesNestedItem() {
        CourseModule parent = module(null, Visibility.HIDDEN, null);
        CourseModule child = module(parent.id(), Visibility.PUBLISHED, null);
        assertThat(LearnerVisibility.itemVisible(course(Visibility.PUBLISHED, null), child, parent,
                item(child, Visibility.PUBLISHED), NOW)).isFalse();
        assertThat(LearnerVisibility.moduleVisible(child, null, NOW)).as("nested module without its parent").isFalse();
    }

    @Test
    void hiddenItemIsNotVisible() {
        CourseModule module = module(null, Visibility.PUBLISHED, null);
        assertThat(LearnerVisibility.itemVisible(course(Visibility.PUBLISHED, null), module, null,
                item(module, Visibility.HIDDEN), NOW)).isFalse();
    }

    @Test
    void scheduledBecomesVisibleAtPublishDate() {
        CourseModule module = module(null, Visibility.SCHEDULED, NOW.plusSeconds(60));
        CourseItem item = item(module, Visibility.PUBLISHED);
        Course course = course(Visibility.PUBLISHED, null);

        assertThat(LearnerVisibility.itemVisible(course, module, null, item, NOW)).isFalse();
        assertThat(LearnerVisibility.itemVisible(course, module, null, item, NOW.plusSeconds(60))).isTrue();
    }

    @Test
    void deletedCourseIsNotVisible() {
        Course deleted = new Course(COURSE, TENANT, null, "C", null, "c", null, null, null, null, Visibility.PUBLISHED, null,
                null, null, null, null, null, 0, NOW, NOW, NOW);
        assertThat(LearnerVisibility.courseVisible(deleted, NOW)).isFalse();
    }

    @Test
    void itemOfAnotherModuleIsNotVisible() {
        CourseModule module = module(null, Visibility.PUBLISHED, null);
        CourseModule other = module(null, Visibility.PUBLISHED, null);
        assertThat(LearnerVisibility.itemVisible(course(Visibility.PUBLISHED, null), module, null,
                item(other, Visibility.PUBLISHED), NOW)).isFalse();
    }
}
