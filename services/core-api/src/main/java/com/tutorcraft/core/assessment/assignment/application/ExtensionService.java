package com.tutorcraft.core.assessment.assignment.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.assessment.assignment.domain.ItemOverride;
import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.domain.Validator;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Индивидуальные и групповые продления срока задания (FR-ASSIGN-03). */
@Service
public class ExtensionService {

    private static final String NOT_FOUND = "assignment.extension_not_found";
    private static final String OBJECT_TYPE = "item_override";

    private final CurrentUserProvider currentUser;
    private final AccessService access;
    private final AssignmentItems items;
    private final ItemOverrideRepository overrides;
    private final EnrollmentApi enrollment;
    private final GraderScope scope;
    private final AuditLog audit;
    private final Clock clock;

    ExtensionService(CurrentUserProvider currentUser, AccessService access, AssignmentItems items,
                     ItemOverrideRepository overrides, EnrollmentApi enrollment, GraderScope scope, AuditLog audit,
                     Clock clock) {
        this.currentUser = currentUser;
        this.access = access;
        this.items = items;
        this.overrides = overrides;
        this.enrollment = enrollment;
        this.scope = scope;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public ExtensionView grant(UUID itemId, ExtensionCommand command) {
        CurrentUser user = currentUser.require();
        AssignmentItem assignment = items.require(user.tenantId(), itemId);
        access.require(Permission.SUBMISSION_GRADE, AccessContext.course(assignment.courseId()));
        validate(command);
        requireTargetInScope(user, assignment, command);
        ItemOverride saved = overrides.upsert(new ItemOverride(Ids.newId(), user.tenantId(), assignment.courseId(), itemId,
                command.userId(), command.groupId(), command.dueAt(), command.closeAt(), user.userId(), clock.instant()));
        audit.record(AuditRecord.of(user.tenantId(), user.userId(), "assignment.extension_granted", OBJECT_TYPE,
                saved.id().toString()).withDiff(diff(saved)));
        return ExtensionView.of(saved);
    }

    @Transactional(readOnly = true)
    public List<ExtensionView> list(UUID itemId) {
        CurrentUser user = currentUser.require();
        AssignmentItem assignment = items.require(user.tenantId(), itemId);
        access.require(Permission.SUBMISSION_VIEW_ALL, AccessContext.course(assignment.courseId()));
        return overrides.ofItem(user.tenantId(), itemId).stream()
                .filter(override -> override.dueAt() != null)
                .map(ExtensionView::of)
                .toList();
    }

    @Transactional
    public void revoke(UUID extensionId) {
        CurrentUser user = currentUser.require();
        ItemOverride override = overrides.find(user.tenantId(), extensionId)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND, "Extension not found"));
        AssignmentItem assignment = items.require(user.tenantId(), override.itemId());
        access.require(Permission.SUBMISSION_GRADE, AccessContext.course(assignment.courseId()));
        requireTargetInScope(user, assignment, new ExtensionCommand(override.userId(), override.groupId(), override.dueAt(), null));
        overrides.delete(user.tenantId(), extensionId);
        audit.record(AuditRecord.of(user.tenantId(), user.userId(), "assignment.extension_revoked", OBJECT_TYPE,
                extensionId.toString()));
    }

    private static void validate(ExtensionCommand command) {
        new Validator()
                .check((command.userId() == null) != (command.groupId() == null), "userId", "exactly_one_target",
                        "Specify either userId or groupId")
                .check(command.dueAt() != null, "dueAt", "required", "Due date is required")
                .check(command.closeAt() == null || command.dueAt() == null || !command.closeAt().isBefore(command.dueAt()),
                        "closeAt", "before_due", "Close date must not be before the due date")
                .throwIfInvalid();
    }

    /** Студент должен быть записан на курс; ассистент продлевает только своим группам (режим separate). */
    private void requireTargetInScope(CurrentUser user, AssignmentItem assignment, ExtensionCommand command) {
        Optional<Set<UUID>> allowed = scope.allowedStudents(user.tenantId(), assignment.courseId(), user.userId());
        if (command.userId() != null) {
            new Validator().check(enrollment.membership(user.tenantId(), assignment.courseId(), command.userId()).isPresent(),
                    "userId", "not_enrolled", "User is not enrolled in the course").throwIfInvalid();
            scope.requireVisible(allowed, Set.of(command.userId()));
            return;
        }
        if (allowed.isPresent()) {
            Set<UUID> groupMembers = enrollment.membersOfGroups(user.tenantId(), assignment.courseId(), Set.of(command.groupId()));
            scope.requireVisible(allowed, groupMembers);
        }
    }

    private static Map<String, Object> diff(ItemOverride override) {
        Map<String, Object> diff = new HashMap<>();
        diff.put("itemId", override.itemId().toString());
        diff.put("userId", override.userId() == null ? null : override.userId().toString());
        diff.put("groupId", override.groupId() == null ? null : override.groupId().toString());
        diff.put("dueAt", String.valueOf(override.dueAt()));
        return diff;
    }

    public record ExtensionCommand(UUID userId, UUID groupId, Instant dueAt, Instant closeAt) {
    }

    public record ExtensionView(UUID id, UUID itemId, UUID userId, UUID groupId, Instant dueAt, Instant closeAt) {

        static ExtensionView of(ItemOverride override) {
            return new ExtensionView(override.id(), override.itemId(), override.userId(), override.groupId(),
                    override.dueAt(), override.closeAt());
        }
    }
}
