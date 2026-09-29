package com.tutorcraft.core.courses.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tutorcraft.core.courses.Visibility;
import com.tutorcraft.core.shared.domain.ValidationException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Инварианты курса: даты, публикация по расписанию, самозапись, корзина. */
class CourseTest {

    private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");

    private static Course.Builder draft() {
        return Course.blank(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NOW).toBuilder().title("Course").slug("course");
    }

    @Test
    void newCourseIsHiddenDraft() {
        Course course = draft().build();
        assertThat(course.visibility()).isEqualTo(Visibility.HIDDEN);
        assertThat(course.selfEnrol()).isEqualTo(SelfEnrolSettings.DISABLED);
        assertThat(course.visibleToLearnersAt(NOW)).isFalse();
        assertThatCode(course::validate).doesNotThrowAnyException();
    }

    @Test
    void titleIsRequiredAndTrimmed() {
        assertThat(draft().title("  Physics ").build().title()).isEqualTo("Physics");
        assertThatThrownBy(() -> draft().title(" ").build().validate()).isInstanceOf(ValidationException.class);
    }

    @Test
    void endMustFollowStart() {
        assertThatThrownBy(() -> draft().startsAt(NOW).endsAt(NOW).build().validate())
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void scheduledRequiresPublishDateAndOtherModesDropIt() {
        assertThatThrownBy(() -> draft().visibility(Visibility.SCHEDULED).build().validate())
                .isInstanceOf(ValidationException.class);
        assertThat(draft().visibility(Visibility.PUBLISHED).publishAt(NOW).build().publishAt()).isNull();
    }

    @Test
    void selfEnrolLimitsAreValidated() {
        assertThatThrownBy(() -> draft().selfEnrol(new SelfEnrolSettings(true, null, 0, null)).build().validate())
                .isInstanceOf(ValidationException.class);
        assertThat(new SelfEnrolSettings(true, "  ", null, null).code()).isNull();
    }

    @Test
    void completionPercentMustBeInRange() {
        assertThatThrownBy(() -> draft().completionRule(new CourseCompletionRule(null, 120.0)).build().validate())
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void refExposesCompletionRuleAndGroupMode() {
        Course course = draft().groupMode(GroupMode.SEPARATE).completionRule(new CourseCompletionRule(null, 60.0)).build();

        assertThat(course.toRef().groupMode()).isEqualTo("separate");
        assertThat(course.toRef().minFinalPercent()).isEqualTo(60.0);
        assertThat(course.toRef().requiredItemIds()).isEmpty();
    }

    @Test
    void trashPolicyUsesRetention() {
        TrashPolicy trash = new TrashPolicy(Duration.ofDays(30));
        Instant deletedAt = NOW.minus(Duration.ofDays(29));

        assertThat(trash.restorable(deletedAt, NOW)).isTrue();
        assertThat(trash.restorable(NOW.minus(Duration.ofDays(30)), NOW)).isFalse();
        assertThat(trash.purgeAt(deletedAt)).isEqualTo(deletedAt.plus(Duration.ofDays(30)));
        assertThat(trash.cutoff(NOW)).isEqualTo(NOW.minus(Duration.ofDays(30)));
    }

    @Test
    void copyTitleFitsLimit() {
        assertThat(CourseTexts.copyTitle("Algebra", " (copy)")).isEqualTo("Algebra (copy)");
        assertThat(CourseTexts.copyTitle("x".repeat(CourseTexts.MAX_TITLE), " (copy)")).hasSize(CourseTexts.MAX_TITLE)
                .endsWith(" (copy)");
    }
}
