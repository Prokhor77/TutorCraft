package com.tutorcraft.core.communication.notifications.infrastructure;

import com.tutorcraft.core.communication.notifications.application.DueItemsSource;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.courses.ItemRef;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** Адаптер порта DueItemsSource поверх публичного API модуля courses. */
@Component
class CoursesDueItemsSource implements DueItemsSource {

    private final CoursesApi courses;

    CoursesDueItemsSource(CoursesApi courses) {
        this.courses = courses;
    }

    @Override
    public List<ItemRef> itemsDueBetween(Instant fromExclusive, Instant toInclusive) {
        return courses.itemsDueBetween(fromExclusive, toInclusive);
    }
}
