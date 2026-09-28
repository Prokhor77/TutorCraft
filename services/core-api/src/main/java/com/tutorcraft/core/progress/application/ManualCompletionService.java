package com.tutorcraft.core.progress.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.progress.LearnerAccess;
import com.tutorcraft.core.progress.domain.CompletionRule;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import com.tutorcraft.core.shared.domain.ForbiddenException;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ручная отметка выполнения студентом (POST/DELETE /items/{id}/complete) — только для режима manual. */
@Service
public class ManualCompletionService {

    private final CoursesApi courses;
    private final CompletionTracker tracker;
    private final LearnerAccess learnerAccess;
    private final AccessService access;
    private final CurrentUserProvider currentUser;

    public ManualCompletionService(CoursesApi courses, CompletionTracker tracker, LearnerAccess learnerAccess,
                                   AccessService access, CurrentUserProvider currentUser) {
        this.courses = courses;
        this.tracker = tracker;
        this.learnerAccess = learnerAccess;
        this.access = access;
        this.currentUser = currentUser;
    }

    @Transactional
    public void mark(UUID itemId, boolean complete) {
        CurrentUser user = currentUser.require();
        ItemRef item = courses.requireItem(user.tenantId(), itemId);
        access.require(Permission.CONTENT_VIEW, AccessContext.course(item.courseId()));
        requireOpen(user, item);
        CompletionRule rule = CompletionRule.of(item.completionMode(), item.completionTriggers());
        if (rule.mode() != CompletionRule.Mode.MANUAL) {
            throw new BusinessRuleException(ProgressErrors.NOT_MANUAL, "Item is not marked complete manually");
        }
        tracker.update(item, user.userId(), state -> state.withManualMark(complete));
    }

    private void requireOpen(CurrentUser user, ItemRef item) {
        if (access.can(Permission.COURSE_VIEW_HIDDEN, AccessContext.course(item.courseId()))) {
            return;
        }
        LearnerAccess.Status status = learnerAccess.statusOf(user.tenantId(), user.userId(), item);
        if (status == LearnerAccess.Status.HIDDEN) {
            throw new NotFoundException(ProgressErrors.ITEM_NOT_FOUND, "Item not found");
        }
        if (status == LearnerAccess.Status.LOCKED) {
            throw new ForbiddenException(ProgressErrors.ITEM_LOCKED, "Item is not available yet");
        }
    }
}
