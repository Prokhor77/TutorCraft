package com.tutorcraft.core.enrollment.application;

import com.tutorcraft.core.shared.domain.NotFoundException;

/** Коды ошибок модуля enrollment (тексты — i18n/enrollment*.properties). */
public final class EnrollmentErrors {

    /** Код модуля courses: курс не найден или недоступен (не раскрываем существование). */
    public static final String COURSE_NOT_FOUND = "course.not_found";
    public static final String ENROLLMENT_NOT_FOUND = "enrollment.not_found";
    public static final String LAST_TEACHER = "enrollment.last_teacher";
    public static final String NOT_ACTIVE = "enrollment.not_active";
    public static final String INVITE_NOT_FOUND = "enrollment.invite_not_found";
    public static final String INVITE_INVALID = "enrollment.invite_invalid";
    public static final String GROUP_NOT_FOUND = "group.not_found";
    public static final String GROUP_NAME_TAKEN = "group.name_taken";
    public static final String AUTO_GROUP_PREFIX = "enrollment.auto_group_prefix";

    private EnrollmentErrors() {
    }

    public static NotFoundException enrollmentNotFound() {
        return new NotFoundException(ENROLLMENT_NOT_FOUND, "Enrollment not found");
    }

    public static NotFoundException courseNotFound() {
        return new NotFoundException(COURSE_NOT_FOUND, "Course not found");
    }

    public static NotFoundException inviteNotFound() {
        return new NotFoundException(INVITE_NOT_FOUND, "Invite link not found");
    }

    public static NotFoundException groupNotFound() {
        return new NotFoundException(GROUP_NOT_FOUND, "Group not found");
    }
}
