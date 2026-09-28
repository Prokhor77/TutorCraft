package com.tutorcraft.core.gradebook.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.courses.CourseRef;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.gradebook.application.GradebookExportWriter.ExportTable;
import com.tutorcraft.core.gradebook.application.GradebookViews.Column;
import com.tutorcraft.core.gradebook.application.GradebookViews.Gradebook;
import com.tutorcraft.core.gradebook.application.GradebookViews.Row;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.i18n.Messages;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Экспорт журнала в CSV/XLSX (FR-GRADE-07). Данные собираются в транзакции, запись в поток — после неё. */
@Service
public class GradebookExportService {

    private static final String FILE_PREFIX = "gradebook-";
    private static final String MAX_SUFFIX = " / ";

    private final CurrentUserProvider currentUser;
    private final AccessService access;
    private final CoursesApi courses;
    private final GradebookTableBuilder tables;
    private final Messages messages;
    private final AuditLog audit;
    private final Map<String, GradebookExportWriter> writers;

    GradebookExportService(CurrentUserProvider currentUser, AccessService access, CoursesApi courses,
                           GradebookTableBuilder tables, Messages messages, AuditLog audit,
                           List<GradebookExportWriter> writers) {
        this.currentUser = currentUser;
        this.access = access;
        this.courses = courses;
        this.tables = tables;
        this.messages = messages;
        this.audit = audit;
        this.writers = writers.stream().collect(Collectors.toUnmodifiableMap(GradebookExportWriter::format, Function.identity()));
    }

    @Transactional
    public ExportFile export(UUID courseId, String format) {
        CurrentUser user = currentUser.require();
        access.require(Permission.GRADE_EXPORT, AccessContext.course(courseId));
        GradebookExportWriter writer = writers.get(format);
        if (writer == null) {
            throw ValidationException.single("format", "invalid", "Supported formats: " + String.join(", ", writers.keySet()));
        }
        CourseRef course = courses.requireCourse(user.tenantId(), courseId);
        ExportTable table = table(course, tables.build(user.tenantId(), courseId, null));
        audit.record(AuditRecord.of(user.tenantId(), user.userId(), "gradebook.exported", "course", courseId.toString())
                .withDiff(Map.of("format", format)));
        String fileName = FILE_PREFIX + (course.shortName() == null ? course.id() : course.shortName()) + "." + format;
        return new ExportFile(fileName, writer.contentType(), out -> writer.write(table, out));
    }

    private ExportTable table(CourseRef course, Gradebook gradebook) {
        List<String> headers = new ArrayList<>();
        headers.add(messages.get("gradebook.export.student"));
        gradebook.columns().forEach(column -> headers.add(column.name() + MAX_SUFFIX + column.maxScore().stripTrailingZeros().toPlainString()));
        headers.add(messages.get("gradebook.export.final_percent"));
        headers.add(messages.get("gradebook.export.final_label"));
        List<List<Object>> rows = gradebook.rows().stream().map(row -> cells(gradebook.columns(), row)).toList();
        return new ExportTable(course.title(), headers, rows);
    }

    private static List<Object> cells(List<Column> columns, Row row) {
        List<Object> cells = new ArrayList<>();
        cells.add(row.userName());
        columns.forEach(column -> cells.add(row.cells().get(column.gradeItemId()).score()));
        cells.add(row.finalPercent());
        cells.add(row.finalLabel());
        return cells;
    }

    /** Файл выгрузки; тело пишется в поток ответа без буферизации всего файла в памяти. */
    public record ExportFile(String fileName, String contentType, Body body) {

        @FunctionalInterface
        public interface Body {

            void writeTo(OutputStream out) throws IOException;
        }
    }
}
