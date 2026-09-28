package com.tutorcraft.core.enrollment.infrastructure;

import com.tutorcraft.core.enrollment.application.EnrollmentErrors;
import com.tutorcraft.core.enrollment.application.GroupRepository;
import com.tutorcraft.core.enrollment.domain.CourseGroup;
import com.tutorcraft.core.shared.domain.ConflictException;
import com.tutorcraft.core.shared.persistence.Timestamps;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcGroupRepository implements GroupRepository {

    private final JdbcClient jdbc;

    JdbcGroupRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(CourseGroup group, Instant now) {
        try {
            jdbc.sql("""
                    INSERT INTO course_groups (id, tenant_id, course_id, name, created_at, updated_at)
                    VALUES (:id, :tenantId, :courseId, :name, :now, :now)
                    """)
                .param("id", group.id()).param("tenantId", group.tenantId()).param("courseId", group.courseId())
                .param("name", group.name()).param("now", Timestamps.of(now))
                .update();
        } catch (DuplicateKeyException e) {
            throw nameTaken();
        }
    }

    @Override
    public Optional<CourseGroup> find(UUID tenantId, UUID id) {
        return jdbc.sql("SELECT id, course_id, name FROM course_groups WHERE tenant_id = :tenantId AND id = :id")
            .param("tenantId", tenantId).param("id", id)
            .query((rs, n) -> new GroupRow(rs.getObject("id", UUID.class), rs.getObject("course_id", UUID.class),
                    rs.getString("name")))
            .optional()
            .map(row -> row.toGroup(tenantId, members(tenantId, List.of(row.id())).getOrDefault(row.id(), List.of())));
    }

    @Override
    public List<CourseGroup> listByCourse(UUID tenantId, UUID courseId) {
        List<GroupRow> rows = jdbc.sql("""
                SELECT id, course_id, name FROM course_groups WHERE tenant_id = :tenantId AND course_id = :courseId
                ORDER BY lower(name)
                """)
            .param("tenantId", tenantId).param("courseId", courseId)
            .query((rs, n) -> new GroupRow(rs.getObject("id", UUID.class), rs.getObject("course_id", UUID.class),
                    rs.getString("name")))
            .list();
        Map<UUID, List<UUID>> members = members(tenantId, rows.stream().map(GroupRow::id).toList());
        return rows.stream().map(row -> row.toGroup(tenantId, members.getOrDefault(row.id(), List.of()))).toList();
    }

    @Override
    public void rename(UUID tenantId, UUID id, String name, Instant now) {
        try {
            jdbc.sql("UPDATE course_groups SET name = :name, updated_at = :now WHERE tenant_id = :tenantId AND id = :id")
                .param("name", name).param("now", Timestamps.of(now)).param("tenantId", tenantId).param("id", id)
                .update();
        } catch (DuplicateKeyException e) {
            throw nameTaken();
        }
    }

    @Override
    public void delete(UUID tenantId, UUID id) {
        jdbc.sql("DELETE FROM course_groups WHERE tenant_id = :tenantId AND id = :id")
            .param("tenantId", tenantId).param("id", id).update();
    }

    @Override
    public void replaceMembers(UUID tenantId, UUID groupId, Collection<UUID> userIds, Instant now) {
        jdbc.sql("DELETE FROM course_group_members WHERE tenant_id = :tenantId AND group_id = :groupId")
            .param("tenantId", tenantId).param("groupId", groupId).update();
        for (UUID userId : new HashSet<>(userIds)) {
            jdbc.sql("""
                    INSERT INTO course_group_members (group_id, user_id, tenant_id, created_at)
                    VALUES (:groupId, :userId, :tenantId, :now)
                    """)
                .param("groupId", groupId).param("userId", userId).param("tenantId", tenantId).param("now", Timestamps.of(now))
                .update();
        }
    }

    @Override
    public Set<UUID> groupIdsOf(UUID tenantId, UUID courseId, UUID userId) {
        return new HashSet<>(jdbc.sql("""
                SELECT m.group_id FROM course_group_members m JOIN course_groups g ON g.id = m.group_id
                WHERE m.tenant_id = :tenantId AND g.course_id = :courseId AND m.user_id = :userId
                """)
            .param("tenantId", tenantId).param("courseId", courseId).param("userId", userId)
            .query(UUID.class).list());
    }

    @Override
    public Map<UUID, List<UUID>> groupIdsOfUsers(UUID tenantId, UUID courseId, Collection<UUID> userIds) {
        Map<UUID, List<UUID>> result = new HashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }
        jdbc.sql("""
                SELECT m.user_id, m.group_id FROM course_group_members m JOIN course_groups g ON g.id = m.group_id
                WHERE m.tenant_id = :tenantId AND g.course_id = :courseId AND m.user_id IN (:userIds)
                """)
            .param("tenantId", tenantId).param("courseId", courseId).param("userIds", List.copyOf(Set.copyOf(userIds)))
            .query((rs, n) -> Map.entry(rs.getObject("user_id", UUID.class), rs.getObject("group_id", UUID.class)))
            .list()
            .forEach(entry -> result.computeIfAbsent(entry.getKey(), key -> new ArrayList<>()).add(entry.getValue()));
        return result;
    }

    @Override
    public Set<UUID> membersOf(UUID tenantId, UUID courseId, Collection<UUID> groupIds) {
        if (groupIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.sql("""
                SELECT DISTINCT m.user_id FROM course_group_members m JOIN course_groups g ON g.id = m.group_id
                WHERE m.tenant_id = :tenantId AND g.course_id = :courseId AND m.group_id IN (:groupIds)
                """)
            .param("tenantId", tenantId).param("courseId", courseId).param("groupIds", List.copyOf(Set.copyOf(groupIds)))
            .query(UUID.class).list());
    }

    @Override
    public void removeUserFromCourseGroups(UUID tenantId, UUID courseId, UUID userId) {
        jdbc.sql("""
                DELETE FROM course_group_members m USING course_groups g
                WHERE g.id = m.group_id AND m.tenant_id = :tenantId AND g.course_id = :courseId AND m.user_id = :userId
                """)
            .param("tenantId", tenantId).param("courseId", courseId).param("userId", userId).update();
    }

    private Map<UUID, List<UUID>> members(UUID tenantId, List<UUID> groupIds) {
        Map<UUID, List<UUID>> result = new LinkedHashMap<>();
        if (groupIds.isEmpty()) {
            return result;
        }
        jdbc.sql("""
                SELECT group_id, user_id FROM course_group_members
                WHERE tenant_id = :tenantId AND group_id IN (:groupIds) ORDER BY created_at, user_id
                """)
            .param("tenantId", tenantId).param("groupIds", groupIds)
            .query((rs, n) -> Map.entry(rs.getObject("group_id", UUID.class), rs.getObject("user_id", UUID.class)))
            .list()
            .forEach(entry -> result.computeIfAbsent(entry.getKey(), key -> new ArrayList<>()).add(entry.getValue()));
        return result;
    }

    private static ConflictException nameTaken() {
        return new ConflictException(EnrollmentErrors.GROUP_NAME_TAKEN, "Group name is already used in this course");
    }

    private record GroupRow(UUID id, UUID courseId, String name) {

        CourseGroup toGroup(UUID tenantId, List<UUID> memberIds) {
            return new CourseGroup(id, tenantId, courseId, name, memberIds);
        }
    }
}
