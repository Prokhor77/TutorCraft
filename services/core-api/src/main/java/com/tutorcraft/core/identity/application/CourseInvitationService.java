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
import com.tutorcraft.core.identity.application.UserRepository.UserFilter;
import com.tutorcraft.core.identity.application.UserRepository.UserSummaryView;
import com.tutorcraft.core.identity.domain.AccountOrigin;
import com.tutorcraft.core.identity.domain.EmailAddress;
import com.tutorcraft.core.identity.domain.UserAccount;
import com.tutorcraft.core.identity.domain.UserStatus;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.domain.Validator;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Преподаватель курса сам приглашает людей (право enrollment.manage на курсе, без администратора платформы):
 * по email — с созданием учётной записи в школе и ссылкой активации, либо выбирая из уже существующих в школе.
 */
@Service
public class CourseInvitationService {

    private static final Logger log = LoggerFactory.getLogger(CourseInvitationService.class);
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_QUERY_LENGTH = 100;
    private static final String COURSE_NOT_FOUND = "course.not_found";

    private final UserRepository users;
    private final InvitedUsers invitedUsers;
    private final InvitationService invitations;
    private final EnrollmentApi enrollment;
    private final CoursesApi courses;
    private final AccessService access;
    private final CurrentUserProvider currentUser;
    private final AuditLog audit;

    public CourseInvitationService(UserRepository users, InvitedUsers invitedUsers, InvitationService invitations,
                                   EnrollmentApi enrollment, CoursesApi courses, AccessService access,
                                   CurrentUserProvider currentUser, AuditLog audit) {
        this.users = users;
        this.invitedUsers = invitedUsers;
        this.invitations = invitations;
        this.enrollment = enrollment;
        this.courses = courses;
        this.access = access;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    /**
     * Находит пользователя школы по email или создаёт приглашённого, затем записывает на курс (повторная запись
     * реактивирует). Ссылка активации возвращается, пока пользователь не принял приглашение.
     */
    @Transactional
    public CourseInvitationResult invite(UUID courseId, InviteCommand command) {
        CurrentUser actor = currentUser.require();
        access.require(Permission.ENROLLMENT_MANAGE, AccessContext.course(courseId));
        validate(command);
        CourseRole role = CourseRole.fromKey(command.role());
        CourseRef course = courses.findCourse(actor.tenantId(), courseId)
                .orElseThrow(() -> new NotFoundException(COURSE_NOT_FOUND, "Course not found"));
        String email = EmailAddress.normalize(command.email());
        Optional<UserAccount> existing = users.findByEmail(actor.tenantId(), email);
        existing.ifPresent(CourseInvitationService::requireNotBlocked);
        UserAccount user = existing.orElseGet(() -> invitedUsers.create(actor.tenantId(), email,
                command.firstName().trim(), command.lastName().trim(), actor.userId(), AccountOrigin.TUTOR_INVITE));
        String activationUrl = user.isInvited()
                ? invitations.sendCourseInvitation(user, actor.userId(), course.title())
                : null;
        enrollment.enrol(new EnrolCommand(actor.tenantId(), courseId, user.id(), role, EnrolCommand.METHOD_MANUAL,
                actor.userId()));
        audit.record(AuditRecord.of(actor.tenantId(), actor.userId(), "user.course_invited", "user", user.id().toString())
                .withContext(courseId.toString())
                .withDiff(Map.of("role", role.key(), "accountCreated", existing.isEmpty())));
        log.info("User {} invited to course {} (account created: {})", user.id(), courseId, existing.isEmpty());
        return new CourseInvitationResult(user.id(), existing.isEmpty(), activationUrl);
    }

    /** Активные пользователи школы для ручной записи на курс (без доступа к админскому списку пользователей). */
    @Transactional(readOnly = true)
    public PageResponse<UserSummaryView> candidates(UUID courseId, String query, PageQuery page) {
        CurrentUser actor = currentUser.require();
        access.require(Permission.ENROLLMENT_MANAGE, AccessContext.course(courseId));
        String trimmed = query == null || query.isBlank() ? null : query.strip();
        new Validator().maxLength(trimmed, MAX_QUERY_LENGTH, "q").throwIfInvalid();
        UserFilter filter = new UserFilter(trimmed, UserStatus.ACTIVE.key(), null, true);
        return page.toPage(users.search(actor.tenantId(), filter, page), UserSummaryView::createdAt, UserSummaryView::id);
    }

    private static void requireNotBlocked(UserAccount user) {
        if (user.isSuspended() || user.isPlatformBlocked()) {
            throw new BusinessRuleException(IdentityErrors.USER_BLOCKED, "User is blocked and cannot be invited");
        }
    }

    private static void validate(InviteCommand command) {
        new Validator()
            .check(EmailAddress.isValid(command.email()), "email", "invalid_email", "Invalid email")
            .notBlank(command.firstName(), "firstName")
            .maxLength(command.firstName(), MAX_NAME_LENGTH, "firstName")
            .notBlank(command.lastName(), "lastName")
            .maxLength(command.lastName(), MAX_NAME_LENGTH, "lastName")
            .notBlank(command.role(), "role")
            .throwIfInvalid();
    }

    public record InviteCommand(String email, String firstName, String lastName, String role) {
    }

    /** @param activationUrl одноразовая ссылка установки пароля; null — учётная запись уже активна */
    public record CourseInvitationResult(UUID userId, boolean accountCreated, String activationUrl) {
    }
}
