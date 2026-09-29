package com.tutorcraft.core.communication.calendar.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tutorcraft.core.communication.NotificationCategory;
import com.tutorcraft.core.communication.NotificationsApi;
import com.tutorcraft.core.communication.NotificationsApi.NotificationCommand;
import com.tutorcraft.core.communication.calendar.domain.Lesson;
import com.tutorcraft.core.communication.calendar.domain.LessonAudience;
import com.tutorcraft.core.courses.CoursesApi;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Кому и какие уведомления уходят при назначении, изменении и отмене занятия. */
class LessonNotifierTest {

    private static final Instant START = Instant.parse("2026-10-05T15:00:00Z");
    private static final UUID ANNA = UUID.randomUUID();
    private static final UUID BORIS = UUID.randomUUID();
    private static final UUID VERA = UUID.randomUUID();
    private static final Set<UUID> ACTIVE = Set.of(ANNA, BORIS, VERA);

    private final NotificationsApi notifications = mock(NotificationsApi.class);
    private final CoursesApi courses = mock(CoursesApi.class);
    private final LessonNotifier notifier = new LessonNotifier(notifications, courses);

    @BeforeEach
    void setUp() {
        when(courses.findCourse(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void newLessonNotifiesItsStudentsInAnnouncementCategory() {
        notifier.created(lesson(List.of(ANNA), START, 0), ACTIVE);

        NotificationCommand command = sent().get(0);
        assertThat(command.userIds()).containsExactly(ANNA);
        assertThat(command.category()).isEqualTo(NotificationCategory.ANNOUNCEMENT);
        assertThat(command.messageCode()).isEqualTo(LessonNotifier.SCHEDULED);
        assertThat(command.link()).isEqualTo(LessonNotifier.LINK);
    }

    @Test
    void updateSplitsRecipientsIntoAddedRemovedAndRescheduled() {
        Lesson before = lesson(List.of(ANNA, BORIS), START, 0);
        Lesson after = lesson(List.of(BORIS, VERA), START.plusSeconds(3600), 1);

        notifier.updated(before, after, ACTIVE);

        Map<String, List<UUID>> byCode = sent().stream()
                .collect(Collectors.toMap(NotificationCommand::messageCode, command -> List.copyOf(command.userIds())));
        assertThat(byCode).containsEntry(LessonNotifier.SCHEDULED, List.of(VERA))
                .containsEntry(LessonNotifier.CANCELLED, List.of(ANNA))
                .containsEntry(LessonNotifier.RESCHEDULED, List.of(BORIS));
    }

    @Test
    void updateWithoutTimeChangeDoesNotSendReschedule() {
        Lesson before = lesson(List.of(ANNA), START, 0);

        notifier.updated(before, lesson(List.of(ANNA), START, 1), ACTIVE);

        verify(notifications, never()).notify(any());
    }

    @Test
    void deleteCancelsForEveryoneAddressed() {
        notifier.deleted(lesson(List.of(), START, 2), ACTIVE);

        NotificationCommand command = sent().get(0);
        assertThat(command.messageCode()).isEqualTo(LessonNotifier.CANCELLED);
        assertThat(command.userIds()).containsExactlyInAnyOrderElementsOf(ACTIVE);
    }

    private List<NotificationCommand> sent() {
        ArgumentCaptor<NotificationCommand> captor = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notifications, atLeastOnce()).notify(captor.capture());
        return captor.getAllValues();
    }

    private static Lesson lesson(List<UUID> attendees, Instant start, long version) {
        LessonAudience audience = attendees.isEmpty() ? LessonAudience.COURSE : LessonAudience.STUDENTS;
        UUID id = UUID.fromString("0192f3c1-0000-7000-8000-00000000000b");
        UUID tenant = UUID.fromString("0192f3c1-0000-7000-8000-00000000000c");
        UUID course = UUID.fromString("0192f3c1-0000-7000-8000-00000000000d");
        return new Lesson(id, tenant, course, null, null, "Дроби", null, start, start.plusSeconds(3600), audience,
                attendees, UUID.randomUUID(), version, START, START);
    }
}
