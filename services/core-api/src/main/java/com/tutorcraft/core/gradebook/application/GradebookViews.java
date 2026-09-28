package com.tutorcraft.core.gradebook.application;

import com.tutorcraft.core.gradebook.domain.Grade;
import com.tutorcraft.core.gradebook.domain.GradeColumn;
import com.tutorcraft.core.gradebook.domain.ScaleLevel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Представления журнала (контракт §9). */
public final class GradebookViews {

    private GradebookViews() {
    }

    public record Column(UUID gradeItemId, String name, BigDecimal maxScore, UUID categoryId) {

        static Column of(GradeColumn column) {
            return new Column(column.id(), column.name(), column.maxScore(), column.categoryId());
        }
    }

    public record Cell(UUID gradeId, BigDecimal score, boolean overridden, boolean locked, boolean published, long version) {

        static final Cell EMPTY = new Cell(null, null, false, false, false, 0);

        static Cell of(Grade grade) {
            return new Cell(grade.id(), grade.finalScore(), grade.overridden(), grade.locked(), grade.published(),
                    grade.version());
        }
    }

    public record Row(UUID userId, String userName, Map<UUID, Cell> cells, BigDecimal finalPercent, String finalLabel) {
    }

    public record Gradebook(List<Column> columns, List<Row> rows) {
    }

    public record SetupCategory(UUID id, String name, BigDecimal weight) {
    }

    public record SetupItem(UUID gradeItemId, String name, BigDecimal maxScore, UUID categoryId, UUID sourceItemId) {

        static SetupItem of(GradeColumn column) {
            return new SetupItem(column.id(), column.name(), column.maxScore(), column.categoryId(), column.sourceItemId());
        }
    }

    public record SetupWarning(String code, String message) {
    }

    public record Setup(String aggregation, List<SetupCategory> categories, List<SetupItem> items, UUID scaleId,
                        String formula, List<SetupWarning> warnings) {
    }

    public record HistoryEntry(Instant at, String actorName, BigDecimal oldScore, BigDecimal newScore) {
    }

    public record MyItem(UUID gradeItemId, String name, BigDecimal score, BigDecimal maxScore, Map<String, Object> feedback) {
    }

    public record MyCourseGrades(UUID courseId, List<MyItem> items, BigDecimal finalPercent, String finalLabel) {
    }

    public record CourseGradeSummary(UUID courseId, String courseTitle, BigDecimal finalPercent, String finalLabel) {
    }

    public record MyGradesOverview(List<CourseGradeSummary> courses) {
    }

    public record ScaleView(UUID id, String name, List<ScaleLevel> levels, UUID courseId) {
    }

    public record QueueEntry(String kind, String id, UUID courseId, String courseTitle, UUID itemId, String itemTitle,
                             UUID userId, String userName, Instant submittedAt, Instant dueAt, boolean late) {
    }
}
