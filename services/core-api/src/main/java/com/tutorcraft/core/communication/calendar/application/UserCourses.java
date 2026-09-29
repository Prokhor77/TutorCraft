package com.tutorcraft.core.communication.calendar.application;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Курсы пользователя для календаря: staff — преподаёт (видит всё), learner — учится (видит только своё),
 * editable — может назначать занятия (course.edit).
 */
record UserCourses(Set<UUID> staff, Set<UUID> learner, Set<UUID> editable) {

    UserCourses {
        staff = Set.copyOf(staff);
        learner = Set.copyOf(learner);
        editable = Set.copyOf(editable);
    }

    Set<UUID> all() {
        Set<UUID> all = new HashSet<>(staff);
        all.addAll(learner);
        return all;
    }

    boolean isEmpty() {
        return staff.isEmpty() && learner.isEmpty();
    }
}
