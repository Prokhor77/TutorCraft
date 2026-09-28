package com.tutorcraft.core.progress.infrastructure;

import com.tutorcraft.core.progress.application.CompletionRepository;
import com.tutorcraft.core.progress.domain.CompletionTrigger;
import com.tutorcraft.core.progress.domain.ItemCompletion;
import com.tutorcraft.core.shared.persistence.Timestamps;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCompletionRepository implements CompletionRepository {

    private static final String COMPLETE = "complete";
    private static final String INCOMPLETE = "incomplete";

    private final JdbcClient jdbc;

    JdbcCompletionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ItemCompletion> find(UUID tenantId, UUID itemId, UUID userId) {
        return jdbc.sql("""
                SELECT tenant_id, course_id, item_id, user_id, viewed, submitted, graded, passed, posted, manual, state,
                       completed_at
                FROM completion_states WHERE tenant_id = :tenantId AND item_id = :itemId AND user_id = :userId
                """)
            .param("tenantId", tenantId).param("itemId", itemId).param("userId", userId)
            .query((rs, n) -> toCompletion(rs)).optional();
    }

    @Override
    public void save(ItemCompletion completion, String source, Instant at) {
        Set<CompletionTrigger> reached = completion.reached();
        jdbc.sql("""
                INSERT INTO completion_states (tenant_id, course_id, item_id, user_id, viewed, submitted, graded, passed, posted,
                                               manual, state, completed_at, source, updated_at)
                VALUES (:tenantId, :courseId, :itemId, :userId, :viewed, :submitted, :graded, :passed, :posted, :manual, :state,
                        :completedAt, :source, :at)
                ON CONFLICT (tenant_id, item_id, user_id) DO UPDATE SET
                    viewed = EXCLUDED.viewed, submitted = EXCLUDED.submitted, graded = EXCLUDED.graded,
                    passed = EXCLUDED.passed, posted = EXCLUDED.posted, manual = EXCLUDED.manual, state = EXCLUDED.state,
                    completed_at = EXCLUDED.completed_at, source = EXCLUDED.source, updated_at = EXCLUDED.updated_at
                """)
            .param("tenantId", completion.tenantId()).param("courseId", completion.courseId())
            .param("itemId", completion.itemId()).param("userId", completion.userId())
            .param("viewed", reached.contains(CompletionTrigger.VIEWED))
            .param("submitted", reached.contains(CompletionTrigger.SUBMITTED))
            .param("graded", reached.contains(CompletionTrigger.GRADED))
            .param("passed", reached.contains(CompletionTrigger.PASSED))
            .param("posted", reached.contains(CompletionTrigger.POSTED))
            .param("manual", completion.manuallyMarked())
            .param("state", completion.complete() ? COMPLETE : INCOMPLETE)
            .param("completedAt", Timestamps.of(completion.completedAt())).param("source", source)
            .param("at", Timestamps.of(at))
            .update();
    }

    @Override
    public Set<UUID> completedItems(UUID tenantId, UUID courseId, UUID userId) {
        return new HashSet<>(jdbc.sql("""
                SELECT item_id FROM completion_states
                WHERE tenant_id = :tenantId AND course_id = :courseId AND user_id = :userId AND state = 'complete'
                """)
            .param("tenantId", tenantId).param("courseId", courseId).param("userId", userId)
            .query((rs, n) -> rs.getObject("item_id", UUID.class)).list());
    }

    @Override
    public Map<UUID, Set<UUID>> completedItemsByCourse(UUID tenantId, UUID userId, Collection<UUID> courseIds) {
        Map<UUID, Set<UUID>> result = new HashMap<>();
        if (courseIds.isEmpty()) {
            return result;
        }
        jdbc.sql("""
                SELECT course_id, item_id FROM completion_states
                WHERE tenant_id = :tenantId AND user_id = :userId AND course_id IN (:courseIds) AND state = 'complete'
                """)
            .param("tenantId", tenantId).param("userId", userId).param("courseIds", List.copyOf(courseIds))
            .query((ResultSet rs) -> {
                result.computeIfAbsent(rs.getObject("course_id", UUID.class), id -> new HashSet<>())
                        .add(rs.getObject("item_id", UUID.class));
            });
        return result;
    }

    @Override
    public Map<UUID, Set<UUID>> completedItemsByUser(UUID tenantId, UUID courseId) {
        Map<UUID, Set<UUID>> result = new HashMap<>();
        jdbc.sql("""
                SELECT user_id, item_id FROM completion_states
                WHERE tenant_id = :tenantId AND course_id = :courseId AND state = 'complete'
                """)
            .param("tenantId", tenantId).param("courseId", courseId)
            .query((ResultSet rs) -> {
                result.computeIfAbsent(rs.getObject("user_id", UUID.class), id -> new HashSet<>())
                        .add(rs.getObject("item_id", UUID.class));
            });
        return result;
    }

    @Override
    public void recordView(UUID tenantId, UUID courseId, UUID itemId, UUID userId, Instant at) {
        jdbc.sql("""
                INSERT INTO item_views (tenant_id, course_id, user_id, item_id, viewed_at)
                VALUES (:tenantId, :courseId, :userId, :itemId, :at)
                ON CONFLICT (tenant_id, course_id, user_id) DO UPDATE SET item_id = EXCLUDED.item_id, viewed_at = EXCLUDED.viewed_at
                """)
            .param("tenantId", tenantId).param("courseId", courseId).param("userId", userId).param("itemId", itemId)
            .param("at", Timestamps.of(at))
            .update();
    }

    @Override
    public Optional<UUID> lastViewedItem(UUID tenantId, UUID courseId, UUID userId) {
        return jdbc.sql("""
                SELECT item_id FROM item_views WHERE tenant_id = :tenantId AND course_id = :courseId AND user_id = :userId
                """)
            .param("tenantId", tenantId).param("courseId", courseId).param("userId", userId)
            .query((rs, n) -> rs.getObject("item_id", UUID.class)).optional();
    }

    @Override
    public Optional<Instant> courseCompletedAt(UUID tenantId, UUID courseId, UUID userId) {
        return jdbc.sql("""
                SELECT completed_at FROM course_completions
                WHERE tenant_id = :tenantId AND course_id = :courseId AND user_id = :userId
                """)
            .param("tenantId", tenantId).param("courseId", courseId).param("userId", userId)
            .query((rs, n) -> Timestamps.read(rs, "completed_at")).optional();
    }

    @Override
    public boolean markCourseCompleted(UUID tenantId, UUID courseId, UUID userId, Instant at) {
        return jdbc.sql("""
                INSERT INTO course_completions (tenant_id, course_id, user_id, completed_at)
                VALUES (:tenantId, :courseId, :userId, :at)
                ON CONFLICT DO NOTHING
                """)
            .param("tenantId", tenantId).param("courseId", courseId).param("userId", userId).param("at", Timestamps.of(at))
            .update() == 1;
    }

    @Override
    public Map<UUID, Instant> courseCompletions(UUID tenantId, UUID courseId) {
        Map<UUID, Instant> result = new HashMap<>();
        jdbc.sql("SELECT user_id, completed_at FROM course_completions WHERE tenant_id = :tenantId AND course_id = :courseId")
            .param("tenantId", tenantId).param("courseId", courseId)
            .query((ResultSet rs) -> {
                result.put(rs.getObject("user_id", UUID.class), Timestamps.read(rs, "completed_at"));
            });
        return result;
    }

    private static ItemCompletion toCompletion(ResultSet rs) throws SQLException {
        Set<CompletionTrigger> reached = EnumSet.noneOf(CompletionTrigger.class);
        addIf(reached, rs.getBoolean("viewed"), CompletionTrigger.VIEWED);
        addIf(reached, rs.getBoolean("submitted"), CompletionTrigger.SUBMITTED);
        addIf(reached, rs.getBoolean("graded"), CompletionTrigger.GRADED);
        addIf(reached, rs.getBoolean("passed"), CompletionTrigger.PASSED);
        addIf(reached, rs.getBoolean("posted"), CompletionTrigger.POSTED);
        return new ItemCompletion(rs.getObject("tenant_id", UUID.class), rs.getObject("course_id", UUID.class),
                rs.getObject("item_id", UUID.class), rs.getObject("user_id", UUID.class), reached, rs.getBoolean("manual"),
                COMPLETE.equals(rs.getString("state")), Timestamps.read(rs, "completed_at"));
    }

    private static void addIf(Set<CompletionTrigger> set, boolean condition, CompletionTrigger trigger) {
        if (condition) {
            set.add(trigger);
        }
    }
}
