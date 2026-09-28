package com.tutorcraft.core.gradebook.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.gradebook.application.GradebookViews.Gradebook;
import com.tutorcraft.core.gradebook.application.GradebookViews.HistoryEntry;
import com.tutorcraft.core.gradebook.domain.Grade;
import com.tutorcraft.core.gradebook.domain.GradeColumn;
import com.tutorcraft.core.identity.UsersApi;
import com.tutorcraft.core.identity.UsersApi.UserRef;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Журнал курса для преподавателя (FR-GRADE-01): студенты × столбцы, итог и словесная оценка, история ячейки. */
@Service
public class GradebookTableService {

    private final CurrentUserProvider currentUser;
    private final AccessService access;
    private final UsersApi users;
    private final GradebookTableBuilder tables;
    private final GradebookStructureRepository structure;
    private final GradeRepository grades;

    GradebookTableService(CurrentUserProvider currentUser, AccessService access, UsersApi users, GradebookTableBuilder tables,
                          GradebookStructureRepository structure, GradeRepository grades) {
        this.currentUser = currentUser;
        this.access = access;
        this.users = users;
        this.tables = tables;
        this.structure = structure;
        this.grades = grades;
    }

    @Transactional(readOnly = true)
    public Gradebook gradebook(UUID courseId, UUID groupId) {
        CurrentUser user = currentUser.require();
        access.require(Permission.GRADE_VIEW_ALL, AccessContext.course(courseId));
        return tables.build(user.tenantId(), courseId, groupId);
    }

    @Transactional(readOnly = true)
    public List<HistoryEntry> history(UUID gradeId) {
        CurrentUser user = currentUser.require();
        Grade grade = grades.find(user.tenantId(), gradeId)
                .orElseThrow(() -> new NotFoundException(GradebookErrors.GRADE_NOT_FOUND, "Grade not found"));
        GradeColumn column = structure.column(user.tenantId(), grade.columnId())
                .orElseThrow(() -> new NotFoundException(GradebookErrors.GRADE_NOT_FOUND, "Grade not found"));
        access.require(Permission.GRADE_VIEW_ALL, AccessContext.course(column.courseId()));
        List<GradeRepository.HistoryRow> rows = grades.history(user.tenantId(), gradeId);
        Map<UUID, UserRef> actors = users.findAll(user.tenantId(),
                rows.stream().map(GradeRepository.HistoryRow::actorId).filter(Objects::nonNull).collect(Collectors.toSet()));
        return rows.stream()
                .map(row -> new HistoryEntry(row.at(), actorName(actors, row.actorId()), row.oldScore(), row.newScore()))
                .toList();
    }

    private static String actorName(Map<UUID, UserRef> actors, UUID actorId) {
        UserRef actor = actorId == null ? null : actors.get(actorId);
        return actor == null ? null : actor.displayName();
    }
}
