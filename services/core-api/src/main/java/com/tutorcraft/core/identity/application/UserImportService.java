package com.tutorcraft.core.identity.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.courses.CourseRef;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.enrollment.EnrollmentApi.EnrolCommand;
import com.tutorcraft.core.identity.application.ImportPreviewRepository.PreviewRow;
import com.tutorcraft.core.identity.domain.EmailAddress;
import com.tutorcraft.core.identity.domain.ImportRow;
import com.tutorcraft.core.identity.domain.ImportRowError;
import com.tutorcraft.core.identity.domain.ImportRowValidator;
import com.tutorcraft.core.identity.domain.UserAccount;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.i18n.Messages;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Массовый импорт пользователей из CSV с предпросмотром и записью на курсы (FR-USER-02). */
@Service
public class UserImportService {

    private static final Duration PREVIEW_TTL = Duration.ofDays(1);
    private static final String MESSAGE_PREFIX = "identity.import.";
    private static final Set<String> COURSE_ROLE_KEYS = Arrays.stream(CourseRole.values())
            .map(CourseRole::key).collect(Collectors.toUnmodifiableSet());

    private final CsvUserImportParser parser;
    private final UserRepository users;
    private final CoursesApi courses;
    private final EnrollmentApi enrollment;
    private final ImportPreviewRepository previews;
    private final InvitedUsers invitedUsers;
    private final InvitationService invitations;
    private final AccessService access;
    private final CurrentUserProvider currentUser;
    private final Messages messages;
    private final AuditLog audit;
    private final Clock clock;

    public UserImportService(CsvUserImportParser parser, UserRepository users, CoursesApi courses, EnrollmentApi enrollment,
                             ImportPreviewRepository previews, InvitedUsers invitedUsers, InvitationService invitations,
                             AccessService access, CurrentUserProvider currentUser, Messages messages, AuditLog audit,
                             Clock clock) {
        this.parser = parser;
        this.users = users;
        this.courses = courses;
        this.enrollment = enrollment;
        this.previews = previews;
        this.invitedUsers = invitedUsers;
        this.invitations = invitations;
        this.access = access;
        this.currentUser = currentUser;
        this.messages = messages;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public ImportPreviewView preview(InputStream input, long sizeBytes) {
        CurrentUser actor = currentUser.require();
        access.require(Permission.USER_IMPORT, AccessContext.tenant());
        List<ImportRow.Raw> raw = parser.parse(input, sizeBytes);
        Map<String, UUID> courseIds = resolveCourses(actor.tenantId(), raw);
        Set<String> existing = users.existingEmails(actor.tenantId(), emailsOf(raw));
        List<ImportRow> rows = new ImportRowValidator(existing, courseIds.keySet(), COURSE_ROLE_KEYS, CourseRole.STUDENT.key())
                .validate(raw).stream().map(this::localizeRow).toList();
        UUID previewId = Ids.newId();
        Instant now = clock.instant();
        previews.insert(previewId, actor.tenantId(), actor.userId(),
                rows.stream().map(row -> new PreviewRow(row, row.hasCourse() ? courseIds.get(row.courseShortName()) : null)).toList(),
                now, now.plus(PREVIEW_TTL));
        audit.record(AuditRecord.of(actor.tenantId(), actor.userId(), "user.import_previewed", "user_import", previewId.toString()));
        return ImportPreviewView.of(previewId, rows);
    }

    @Transactional
    public ImportCommitResult commit(UUID previewId) {
        CurrentUser actor = currentUser.require();
        access.require(Permission.USER_IMPORT, AccessContext.tenant());
        List<PreviewRow> rows = previews.findActive(actor.tenantId(), previewId, actor.userId(), clock.instant())
                .orElseThrow(UserImportService::previewNotFound);
        if (!previews.markCommitted(actor.tenantId(), previewId, clock.instant())) {
            throw previewNotFound();
        }
        Tally tally = new Tally();
        rows.forEach(row -> apply(actor, row, tally));
        audit.record(AuditRecord.of(actor.tenantId(), actor.userId(), "user.import_committed", "user_import", previewId.toString())
                .withDiff(Map.of("created", tally.created, "enrolled", tally.enrolled, "errors", tally.errors.size())));
        return new ImportCommitResult(tally.created, tally.enrolled, List.copyOf(tally.errors));
    }

    private void apply(CurrentUser actor, PreviewRow preview, Tally tally) {
        ImportRow row = preview.row();
        if (!row.valid()) {
            tally.errors.addAll(row.errors());
            return;
        }
        Optional<CourseRef> course = Optional.ofNullable(preview.courseId())
                .flatMap(courseId -> courses.findCourse(actor.tenantId(), courseId));
        if (row.hasCourse() && course.isEmpty()) {
            tally.errors.add(localizeError(new ImportRowError(row.row(), ImportRowValidator.FIELD_COURSE,
                    ImportRowValidator.UNKNOWN_COURSE, null)));
            return;
        }
        UserAccount user = findOrInvite(actor, row, tally);
        course.ifPresent(found -> enrol(actor, user, found, row.roleKey(), tally));
    }

    private UserAccount findOrInvite(CurrentUser actor, ImportRow row, Tally tally) {
        Optional<UserAccount> existing = users.findByEmail(actor.tenantId(), row.email());
        if (existing.isPresent()) {
            return existing.get();
        }
        UserAccount created = invitedUsers.create(actor.tenantId(), row.email(), row.firstName(), row.lastName());
        invitations.sendInvitation(created, actor.userId());
        tally.created++;
        return created;
    }

    private void enrol(CurrentUser actor, UserAccount user, CourseRef course, String roleKey, Tally tally) {
        enrollment.enrol(new EnrolCommand(actor.tenantId(), course.id(), user.id(), CourseRole.fromKey(roleKey),
                EnrolCommand.METHOD_IMPORT, actor.userId()));
        tally.enrolled++;
    }

    private Map<String, UUID> resolveCourses(UUID tenantId, List<ImportRow.Raw> raw) {
        return raw.stream()
                .map(ImportRow.Raw::courseShortName)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .distinct()
                .flatMap(name -> courses.findByShortName(tenantId, name).map(course -> Map.entry(name, course.id())).stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static NotFoundException previewNotFound() {
        return new NotFoundException(IdentityErrors.IMPORT_PREVIEW_NOT_FOUND, "Import preview not found");
    }

    private static List<String> emailsOf(List<ImportRow.Raw> raw) {
        return raw.stream().map(row -> EmailAddress.normalize(row.email()))
                .filter(EmailAddress::isValid)
                .distinct()
                .toList();
    }

    private ImportRow localizeRow(ImportRow row) {
        List<ImportRowError> errors = row.errors().stream().map(this::localizeError).toList();
        return new ImportRow(row.row(), row.email(), row.firstName(), row.lastName(), row.courseShortName(), row.roleKey(),
                row.existingUser(), errors);
    }

    private ImportRowError localizeError(ImportRowError error) {
        return error.withMessage(messages.get(MESSAGE_PREFIX + error.code()));
    }

    /** Счётчики применения импорта. */
    private static final class Tally {
        private int created;
        private int enrolled;
        private final List<ImportRowError> errors = new ArrayList<>();
    }

    public record ImportPreviewView(UUID previewId, int valid, int invalid, List<PreviewRowView> rows) {

        static ImportPreviewView of(UUID previewId, List<ImportRow> rows) {
            int valid = (int) rows.stream().filter(ImportRow::valid).count();
            List<PreviewRowView> views = rows.stream().map(PreviewRowView::of).toList();
            return new ImportPreviewView(previewId, valid, rows.size() - valid, views);
        }
    }

    public record PreviewRowView(int row, String email, String firstName, String lastName, String courseShortName,
                                 List<ImportRowError> errors) {

        static PreviewRowView of(ImportRow row) {
            return new PreviewRowView(row.row(), row.email(), row.firstName(), row.lastName(), row.courseShortName(), row.errors());
        }
    }

    public record ImportCommitResult(int created, int enrolled, List<ImportRowError> errors) {
    }
}
