package com.tutorcraft.core.enrollment;

import com.tutorcraft.core.access.domain.CourseRole;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Публичный API модуля enrollment. */
public interface EnrollmentApi {

    /** Курсы с активной записью пользователя в одной из ролей. */
    List<UUID> activeCourseIds(UUID tenantId, UUID userId, Set<CourseRole> roles);

    /** Активные участники курса в указанных ролях. */
    List<Member> activeMembers(UUID tenantId, UUID courseId, Set<CourseRole> roles);

    Optional<Member> membership(UUID tenantId, UUID courseId, UUID userId);

    Set<UUID> groupIds(UUID tenantId, UUID courseId, UUID userId);

    /** Участники групп курса (для фильтров «группа» в журнале/проверке). */
    Set<UUID> membersOfGroups(UUID tenantId, UUID courseId, Collection<UUID> groupIds);

    /** Идемпотентная запись (повторная — реактивирует). Используется импортом, приглашениями и демо-данными. */
    void enrol(EnrolCommand command);

    record Member(UUID userId, CourseRole role, String status, Set<UUID> groupIds) {
    }

    record EnrolCommand(UUID tenantId, UUID courseId, UUID userId, CourseRole role, String method, UUID actorId) {

        public static final String METHOD_MANUAL = "manual";
        public static final String METHOD_SELF = "self";
        public static final String METHOD_INVITE_LINK = "invite_link";
        public static final String METHOD_IMPORT = "import";
    }
}
