package com.tutorcraft.core.enrollment.application;

import com.tutorcraft.core.enrollment.domain.CourseGroup;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Группы курса и их участники (PostgreSQL). */
public interface GroupRepository {

    /** @throws com.tutorcraft.core.shared.domain.ConflictException имя группы занято в курсе */
    void insert(CourseGroup group, Instant now);

    Optional<CourseGroup> find(UUID tenantId, UUID id);

    List<CourseGroup> listByCourse(UUID tenantId, UUID courseId);

    /** @throws com.tutorcraft.core.shared.domain.ConflictException имя группы занято в курсе */
    void rename(UUID tenantId, UUID id, String name, Instant now);

    void delete(UUID tenantId, UUID id);

    void replaceMembers(UUID tenantId, UUID groupId, Collection<UUID> userIds, Instant now);

    Set<UUID> groupIdsOf(UUID tenantId, UUID courseId, UUID userId);

    /** userId → группы курса, для списка участников. */
    Map<UUID, List<UUID>> groupIdsOfUsers(UUID tenantId, UUID courseId, Collection<UUID> userIds);

    Set<UUID> membersOf(UUID tenantId, UUID courseId, Collection<UUID> groupIds);

    void removeUserFromCourseGroups(UUID tenantId, UUID courseId, UUID userId);
}
