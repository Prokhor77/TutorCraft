package com.tutorcraft.core.enrollment.application;

import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.courses.CourseRef;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.courses.CoursesApi.SelfEnrolment;
import com.tutorcraft.core.enrollment.EnrollmentApi.EnrolCommand;
import com.tutorcraft.core.enrollment.application.EnrollmentRepository.UpsertMode;
import com.tutorcraft.core.enrollment.application.EnrollmentRepository.UpsertResult;
import com.tutorcraft.core.enrollment.domain.Enrollment;
import com.tutorcraft.core.enrollment.domain.EnrollmentStatus;
import com.tutorcraft.core.enrollment.domain.InviteLink;
import com.tutorcraft.core.enrollment.domain.SelfEnrolPolicy;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import com.tutorcraft.core.shared.security.TokenHasher;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Вход учащегося на курс сам: самозапись (FR-ENROL-02) и принятие ссылки-приглашения (FR-ENROL-03).
 * Курс ищется в tenant пользователя — чужой tenant → 404. Уже активная запись возвращается без изменений;
 * приостановленная/завершённая — не реактивируется самим пользователем (422 enrollment.not_active).
 */
@Service
public class JoinService {

    private final EnrollmentRepository enrollments;
    private final InviteLinkRepository links;
    private final CoursesApi courses;
    private final CurrentUserProvider currentUser;
    private final EnrollmentChanges changes;
    private final EnrollmentViews views;
    private final Clock clock;

    public JoinService(EnrollmentRepository enrollments, InviteLinkRepository links, CoursesApi courses,
                       CurrentUserProvider currentUser, EnrollmentChanges changes, EnrollmentViews views, Clock clock) {
        this.enrollments = enrollments;
        this.links = links;
        this.courses = courses;
        this.currentUser = currentUser;
        this.changes = changes;
        this.views = views;
        this.clock = clock;
    }

    /** Платный курс без кода → 422 enrollment.payment_required (путь — покупка, POST /courses/{id}/orders). */
    @Transactional
    public EnrollmentView selfEnrol(UUID courseId, String code) {
        CurrentUser user = currentUser.require();
        Instant now = clock.instant();
        CourseRef course = requireVisibleCourse(user.tenantId(), courseId, now);
        Optional<Enrollment> existing = existingMembership(user, courseId);
        if (existing.isPresent()) {
            return views.view(existing.get());
        }
        enrollments.lockCourse(courseId);
        SelfEnrolment settings = courses.selfEnrolment(user.tenantId(), courseId).orElseThrow(EnrollmentErrors::courseNotFound);
        SelfEnrolPolicy.check(new SelfEnrolPolicy.Request(settings.enabled(), settings.code(), settings.maxStudents(),
                settings.until(), course.price() != null, code,
                enrollments.countActiveByRole(user.tenantId(), courseId, CourseRole.STUDENT), now));
        return views.view(join(user, courseId, CourseRole.STUDENT, EnrolCommand.METHOD_SELF));
    }

    /** Ссылка другого tenant или неизвестный токен → 404; истёкшая/отозванная/исчерпанная → 422. */
    @Transactional
    public UUID acceptInvite(String token) {
        CurrentUser user = currentUser.require();
        InviteLink link = links.findByTokenHash(user.tenantId(), TokenHasher.sha256(token))
                .orElseThrow(EnrollmentErrors::inviteNotFound);
        courses.findCourse(user.tenantId(), link.courseId()).orElseThrow(EnrollmentErrors::inviteNotFound);
        if (existingMembership(user, link.courseId()).isPresent()) {
            return link.courseId();
        }
        if (!links.consume(user.tenantId(), link.id(), clock.instant())) {
            throw new BusinessRuleException(EnrollmentErrors.INVITE_INVALID, "Invite link is no longer valid",
                    Map.of("reason", link.validityAt(clock.instant()).name().toLowerCase(Locale.ROOT)));
        }
        join(user, link.courseId(), link.role(), EnrolCommand.METHOD_INVITE_LINK);
        changes.audit(user.tenantId(), user.userId(), EnrollmentChanges.OBJECT_INVITE_LINK, link.id(), "accepted",
                link.courseId(), null);
        return link.courseId();
    }

    private CourseRef requireVisibleCourse(UUID tenantId, UUID courseId, Instant now) {
        return courses.findCourse(tenantId, courseId)
                .filter(course -> course.visibility().visibleAt(course.publishAt(), now))
                .orElseThrow(EnrollmentErrors::courseNotFound);
    }

    /** Существующая активная запись; неактивная → 422 (реактивирует только преподаватель). */
    private Optional<Enrollment> existingMembership(CurrentUser user, UUID courseId) {
        Optional<Enrollment> existing = enrollments.findByCourseAndUser(user.tenantId(), courseId, user.userId());
        if (existing.isPresent() && existing.get().status() != EnrollmentStatus.ACTIVE) {
            throw new BusinessRuleException(EnrollmentErrors.NOT_ACTIVE, "Enrollment is suspended or completed");
        }
        return existing;
    }

    private Enrollment join(CurrentUser user, UUID courseId, CourseRole role, String method) {
        Instant now = clock.instant();
        Enrollment enrollment = new Enrollment(Ids.newId(), user.tenantId(), courseId, user.userId(), role,
                EnrollmentStatus.ACTIVE, method, null, null, now, now, null);
        UpsertResult result = enrollments.upsert(enrollment, UpsertMode.REACTIVATE);
        changes.audit(user.tenantId(), user.userId(), EnrollmentChanges.OBJECT_ENROLLMENT, result.id(), "created", courseId,
                Map.of("userId", user.userId().toString(), "role", role.key(), "method", method));
        changes.enrollmentChanged(user.tenantId(), courseId, user.userId(), result.roleKey(), EnrollmentStatus.ACTIVE.key(),
                method, result.created());
        return enrollments.find(user.tenantId(), result.id()).orElseThrow(EnrollmentErrors::enrollmentNotFound);
    }
}
