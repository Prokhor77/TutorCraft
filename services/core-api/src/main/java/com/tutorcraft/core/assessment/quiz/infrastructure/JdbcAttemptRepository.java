package com.tutorcraft.core.assessment.quiz.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.tutorcraft.core.assessment.quiz.application.AttemptRepository;
import com.tutorcraft.core.assessment.quiz.domain.AnswerRecord;
import com.tutorcraft.core.assessment.quiz.domain.Attempt;
import com.tutorcraft.core.assessment.quiz.domain.AttemptSlot;
import com.tutorcraft.core.assessment.quiz.domain.AttemptState;
import com.tutorcraft.core.assessment.quiz.domain.GradingMethod.ScoredAttempt;
import com.tutorcraft.core.shared.api.CursorCodec;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import com.tutorcraft.core.shared.persistence.JsonCodec;
import com.tutorcraft.core.shared.persistence.Timestamps;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAttemptRepository implements AttemptRepository {

    private static final TypeReference<List<AttemptSlot>> LAYOUT = new TypeReference<>() { };
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };
    private static final String COLUMNS = """
            id, tenant_id, course_id, item_id, user_id, number, state, started_at, time_due, finished_at, score, max_score,
            needs_manual, layout::text AS layout
            """;
    private static final String ANSWER_COLUMNS = """
            attempt_id, slot, question_version_id, response::text AS response, fraction, score, needs_manual, graded_by,
            comment, flagged, saved_at
            """;

    private final JdbcClient jdbc;
    private final JsonCodec json;

    JdbcAttemptRepository(JdbcClient jdbc, JsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public boolean insert(Attempt attempt) {
        int inserted = jdbc.sql("""
                INSERT INTO quiz_attempts (id, tenant_id, course_id, item_id, user_id, number, state, started_at, time_due,
                                           max_score, needs_manual, layout)
                VALUES (:id, :tenantId, :courseId, :itemId, :userId, :number, :state, :startedAt, :timeDue, :maxScore, false,
                        :layout)
                ON CONFLICT DO NOTHING
                """)
            .param("id", attempt.id()).param("tenantId", attempt.tenantId()).param("courseId", attempt.courseId())
            .param("itemId", attempt.itemId()).param("userId", attempt.userId()).param("number", attempt.number())
            .param("state", attempt.state().key()).param("startedAt", Timestamps.of(attempt.startedAt()))
            .param("timeDue", Timestamps.of(attempt.timeDue())).param("maxScore", attempt.maxScore())
            .param("layout", json.toJsonb(attempt.layout()))
            .update();
        if (inserted == 0) {
            return false;
        }
        insertAnswerRows(attempt);
        return true;
    }

    private void insertAnswerRows(Attempt attempt) {
        jdbc.sql("""
                INSERT INTO attempt_answers (attempt_id, tenant_id, slot, question_version_id)
                SELECT :attemptId, :tenantId, s.slot, s."questionVersionId"
                FROM jsonb_to_recordset(CAST(:layout AS jsonb)) AS s(slot int, "questionVersionId" uuid)
                """)
            .param("attemptId", attempt.id()).param("tenantId", attempt.tenantId())
            .param("layout", json.toJsonb(attempt.layout()))
            .update();
    }

    @Override
    public Optional<Attempt> find(UUID tenantId, UUID attemptId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM quiz_attempts WHERE tenant_id = :tenantId AND id = :id")
                .param("tenantId", tenantId).param("id", attemptId)
                .query((rs, n) -> toAttempt(rs)).optional();
    }

    @Override
    public Optional<Attempt> lock(UUID tenantId, UUID attemptId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM quiz_attempts WHERE tenant_id = :tenantId AND id = :id FOR UPDATE")
                .param("tenantId", tenantId).param("id", attemptId)
                .query((rs, n) -> toAttempt(rs)).optional();
    }

    @Override
    public Optional<Attempt> findInProgress(UUID tenantId, UUID itemId, UUID userId) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM quiz_attempts
                WHERE tenant_id = :tenantId AND item_id = :itemId AND user_id = :userId AND state = 'in_progress'
                """)
            .param("tenantId", tenantId).param("itemId", itemId).param("userId", userId)
            .query((rs, n) -> toAttempt(rs)).optional();
    }

    @Override
    public int countAttempts(UUID tenantId, UUID itemId, UUID userId) {
        return jdbc.sql("""
                SELECT CAST(COALESCE(MAX(number), 0) AS int) FROM quiz_attempts
                WHERE tenant_id = :tenantId AND item_id = :itemId AND user_id = :userId
                """)
            .param("tenantId", tenantId).param("itemId", itemId).param("userId", userId)
            .query(Integer.class).single();
    }

    @Override
    public boolean saveAnswer(AnswerSave save) {
        return jdbc.sql("""
                UPDATE attempt_answers a
                SET response = COALESCE(CAST(:response AS jsonb), a.response),
                    flagged = COALESCE(CAST(:flagged AS boolean), a.flagged),
                    saved_at = :savedAt
                FROM quiz_attempts q
                WHERE a.attempt_id = :attemptId AND a.slot = :slot AND a.tenant_id = :tenantId
                  AND q.id = a.attempt_id AND q.tenant_id = :tenantId AND q.user_id = :userId AND q.state = 'in_progress'
                  AND (q.time_due IS NULL OR q.time_due >= :cutoff)
                """)
            .param("response", json.toJsonb(save.response())).param("flagged", save.flagged())
            .param("savedAt", Timestamps.of(save.savedAt())).param("attemptId", save.attemptId())
            .param("slot", save.slot()).param("tenantId", save.tenantId()).param("userId", save.userId())
            .param("cutoff", Timestamps.of(save.cutoff()))
            .update() == 1;
    }

    @Override
    public List<AnswerRecord> answers(UUID tenantId, UUID attemptId) {
        return jdbc.sql("SELECT " + ANSWER_COLUMNS + """
                 FROM attempt_answers WHERE tenant_id = :tenantId AND attempt_id = :attemptId ORDER BY slot
                """)
            .param("tenantId", tenantId).param("attemptId", attemptId)
            .query((rs, n) -> toAnswer(rs)).list();
    }

    @Override
    public Optional<AnswerRecord> answer(UUID tenantId, UUID attemptId, int slot) {
        return jdbc.sql("SELECT " + ANSWER_COLUMNS + """
                 FROM attempt_answers WHERE tenant_id = :tenantId AND attempt_id = :attemptId AND slot = :slot
                """)
            .param("tenantId", tenantId).param("attemptId", attemptId).param("slot", slot)
            .query((rs, n) -> toAnswer(rs)).optional();
    }

    @Override
    public void updateAnswerGrade(UUID tenantId, AnswerGrade grade) {
        jdbc.sql("""
                UPDATE attempt_answers
                SET question_version_id = :versionId, fraction = :fraction, score = :score, needs_manual = :needsManual,
                    graded_by = :gradedBy, graded_at = :gradedAt, comment = :comment
                WHERE tenant_id = :tenantId AND attempt_id = :attemptId AND slot = :slot
                """)
            .param("versionId", grade.questionVersionId()).param("fraction", grade.fraction()).param("score", grade.score())
            .param("needsManual", grade.needsManual()).param("gradedBy", grade.gradedBy())
            .param("gradedAt", Timestamps.of(grade.gradedAt())).param("comment", grade.comment())
            .param("tenantId", tenantId).param("attemptId", grade.attemptId()).param("slot", grade.slot())
            .update();
    }

    @Override
    public boolean markFinished(UUID tenantId, UUID attemptId, Instant finishedAt, BigDecimal score, boolean needsManual) {
        return jdbc.sql("""
                UPDATE quiz_attempts SET state = 'finished', finished_at = :finishedAt, score = :score, needs_manual = :needsManual
                WHERE tenant_id = :tenantId AND id = :id AND state = 'in_progress'
                """)
            .param("finishedAt", Timestamps.of(finishedAt)).param("score", score).param("needsManual", needsManual)
            .param("tenantId", tenantId).param("id", attemptId)
            .update() == 1;
    }

    @Override
    public void updateScore(UUID tenantId, UUID attemptId, BigDecimal score, boolean needsManual) {
        jdbc.sql("UPDATE quiz_attempts SET score = :score, needs_manual = :needsManual WHERE tenant_id = :tenantId AND id = :id")
            .param("score", score).param("needsManual", needsManual).param("tenantId", tenantId).param("id", attemptId)
            .update();
    }

    @Override
    public List<ScoredAttempt> finishedScores(UUID tenantId, UUID itemId, UUID userId) {
        return jdbc.sql("""
                SELECT number, score FROM quiz_attempts
                WHERE tenant_id = :tenantId AND item_id = :itemId AND user_id = :userId AND state = 'finished'
                  AND score IS NOT NULL
                """)
            .param("tenantId", tenantId).param("itemId", itemId).param("userId", userId)
            .query((rs, n) -> new ScoredAttempt(rs.getInt("number"), rs.getBigDecimal("score"))).list();
    }

    @Override
    public boolean hasPendingManual(UUID tenantId, UUID itemId, UUID userId) {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM quiz_attempts
                               WHERE tenant_id = :tenantId AND item_id = :itemId AND user_id = :userId
                                 AND state = 'finished' AND needs_manual)
                """)
            .param("tenantId", tenantId).param("itemId", itemId).param("userId", userId)
            .query(Boolean.class).single();
    }

    @Override
    public List<Attempt> finishedOfItem(UUID tenantId, UUID itemId) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM quiz_attempts WHERE tenant_id = :tenantId AND item_id = :itemId AND state = 'finished'
                ORDER BY started_at
                """)
            .param("tenantId", tenantId).param("itemId", itemId)
            .query((rs, n) -> toAttempt(rs)).list();
    }

    @Override
    public PageResponse<Attempt> pageOfItem(UUID tenantId, UUID itemId, PageQuery page) {
        CursorCodec.Position after = page.after().orElse(null);
        List<Attempt> rows = jdbc.sql("SELECT " + COLUMNS + """
                 FROM quiz_attempts
                WHERE tenant_id = :tenantId AND item_id = :itemId
                  AND (CAST(:afterAt AS timestamptz) IS NULL OR (started_at, id) < (:afterAt, :afterId))
                ORDER BY started_at DESC, id DESC
                LIMIT :limit
                """)
            .param("tenantId", tenantId).param("itemId", itemId)
            .param("afterAt", after == null ? null : Timestamps.of(after.sortKey()))
            .param("afterId", after == null ? null : after.id())
            .param("limit", page.fetchSize())
            .query((rs, n) -> toAttempt(rs)).list();
        return page.toPage(rows, Attempt::startedAt, Attempt::id);
    }

    @Override
    public List<AttemptKey> findExpired(Instant cutoff, int limit) {
        return jdbc.sql("""
                SELECT tenant_id, id FROM quiz_attempts
                WHERE state = 'in_progress' AND time_due IS NOT NULL AND time_due < :cutoff
                ORDER BY time_due LIMIT :limit
                """)
            .param("cutoff", Timestamps.of(cutoff)).param("limit", limit)
            .query((rs, n) -> new AttemptKey(rs.getObject("tenant_id", UUID.class), rs.getObject("id", UUID.class))).list();
    }

    @Override
    public Map<UUID, ItemAttemptState> itemStates(UUID tenantId, UUID userId, Collection<UUID> itemIds) {
        Map<UUID, ItemAttemptState> states = new HashMap<>();
        jdbc.sql("""
                SELECT item_id, bool_or(state = 'in_progress') AS in_progress,
                       CAST(count(*) FILTER (WHERE state = 'finished') AS int) AS finished,
                       bool_or(state = 'finished' AND needs_manual) AS pending
                FROM quiz_attempts
                WHERE tenant_id = :tenantId AND user_id = :userId AND item_id IN (:itemIds)
                GROUP BY item_id
                """)
            .param("tenantId", tenantId).param("userId", userId).param("itemIds", List.copyOf(itemIds))
            .query((ResultSet rs) -> {
                states.put(rs.getObject("item_id", UUID.class), new ItemAttemptState(rs.getBoolean("in_progress"),
                        rs.getInt("finished"), rs.getBoolean("pending")));
            });
        return states;
    }

    @Override
    public List<PendingEssay> pendingEssays(UUID tenantId, Collection<UUID> courseIds) {
        return jdbc.sql("""
                SELECT a.attempt_id, a.slot, q.course_id, q.item_id, q.user_id, q.finished_at
                FROM attempt_answers a JOIN quiz_attempts q ON q.id = a.attempt_id
                WHERE q.tenant_id = :tenantId AND q.course_id IN (:courseIds) AND q.state = 'finished' AND q.needs_manual
                  AND a.tenant_id = :tenantId AND a.needs_manual AND a.graded_by IS NULL
                ORDER BY q.finished_at, a.attempt_id, a.slot
                """)
            .param("tenantId", tenantId).param("courseIds", List.copyOf(courseIds))
            .query((rs, n) -> new PendingEssay(rs.getObject("attempt_id", UUID.class), rs.getInt("slot"),
                    rs.getObject("course_id", UUID.class), rs.getObject("item_id", UUID.class),
                    rs.getObject("user_id", UUID.class), Timestamps.read(rs, "finished_at")))
            .list();
    }

    @Override
    public boolean userSawQuestion(UUID tenantId, UUID userId, UUID questionId) {
        String probe = json.write(List.of(Map.of("questionId", questionId.toString())));
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM quiz_attempts
                               WHERE tenant_id = :tenantId AND user_id = :userId AND layout @> CAST(:probe AS jsonb))
                """)
            .param("tenantId", tenantId).param("userId", userId).param("probe", probe)
            .query(Boolean.class).single();
    }

    private Attempt toAttempt(ResultSet rs) throws SQLException {
        return new Attempt(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("course_id", UUID.class), rs.getObject("item_id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getInt("number"), AttemptState.fromKey(rs.getString("state")), Timestamps.read(rs, "started_at"),
                Timestamps.read(rs, "time_due"), Timestamps.read(rs, "finished_at"), rs.getBigDecimal("score"),
                rs.getBigDecimal("max_score"), rs.getBoolean("needs_manual"), json.read(rs.getString("layout"), LAYOUT));
    }

    private AnswerRecord toAnswer(ResultSet rs) throws SQLException {
        BigDecimal fraction = rs.getBigDecimal("fraction");
        return new AnswerRecord(rs.getObject("attempt_id", UUID.class), rs.getInt("slot"),
                rs.getObject("question_version_id", UUID.class), json.read(rs.getString("response"), MAP),
                fraction == null ? null : fraction.doubleValue(), rs.getBigDecimal("score"), rs.getBoolean("needs_manual"),
                rs.getObject("graded_by", UUID.class), rs.getString("comment"), rs.getBoolean("flagged"),
                Timestamps.read(rs, "saved_at"));
    }
}
