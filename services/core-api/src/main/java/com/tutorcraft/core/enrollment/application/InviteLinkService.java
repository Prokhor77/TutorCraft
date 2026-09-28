package com.tutorcraft.core.enrollment.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.enrollment.domain.InviteLink;
import com.tutorcraft.core.shared.config.AppProperties;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import com.tutorcraft.core.shared.security.TokenHasher;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ссылки-приглашения (FR-ENROL-03): токен 256 бит показывается один раз, в БД — только SHA-256 (NFR-SEC-07). */
@Service
public class InviteLinkService {

    private static final String JOIN_PATH = "/join/";

    private final InviteLinkRepository links;
    private final AccessService access;
    private final CurrentUserProvider currentUser;
    private final EnrollmentChanges changes;
    private final String publicBaseUrl;
    private final Clock clock;

    public InviteLinkService(InviteLinkRepository links, AccessService access, CurrentUserProvider currentUser,
                             EnrollmentChanges changes, AppProperties properties, Clock clock) {
        this.links = links;
        this.access = access;
        this.currentUser = currentUser;
        this.changes = changes;
        this.publicBaseUrl = properties.publicBaseUrl().replaceAll("/+$", "");
        this.clock = clock;
    }

    @Transactional
    public InviteLinkView.Created create(UUID courseId, String role, Instant expiresAt, Integer maxUses) {
        CurrentUser actor = currentUser.require();
        access.require(Permission.ENROLLMENT_MANAGE, AccessContext.course(courseId));
        CourseRole courseRole = CourseRole.fromKey(role);
        Instant now = clock.instant();
        InviteLink.validateNew(expiresAt, maxUses, now);
        String token = TokenHasher.newToken();
        InviteLink link = new InviteLink(Ids.newId(), actor.tenantId(), courseId, TokenHasher.sha256(token), courseRole,
                expiresAt, maxUses, 0, null, actor.userId(), now);
        links.insert(link);
        Map<String, Object> diff = new HashMap<>();
        diff.put("role", courseRole.key());
        diff.put("maxUses", maxUses);
        diff.put("expiresAt", expiresAt == null ? null : expiresAt.toString());
        changes.audit(actor.tenantId(), actor.userId(), EnrollmentChanges.OBJECT_INVITE_LINK, link.id(), "created", courseId, diff);
        return new InviteLinkView.Created(link.id(), publicBaseUrl + JOIN_PATH + token);
    }

    @Transactional(readOnly = true)
    public List<InviteLinkView> list(UUID courseId) {
        CurrentUser actor = currentUser.require();
        access.require(Permission.ENROLLMENT_MANAGE, AccessContext.course(courseId));
        Instant now = clock.instant();
        return links.listByCourse(actor.tenantId(), courseId).stream()
                .map(link -> new InviteLinkView(link.id(), link.role().key(), link.expiresAt(), link.maxUses(), link.uses(),
                        link.revokedAt(), link.createdAt(), link.usableAt(now)))
                .toList();
    }

    /** Отзыв (DELETE /invite-links/{id}); повторный — без ошибки. */
    @Transactional
    public void revoke(UUID linkId) {
        CurrentUser actor = currentUser.require();
        InviteLink link = links.find(actor.tenantId(), linkId).orElseThrow(EnrollmentErrors::inviteNotFound);
        access.require(Permission.ENROLLMENT_MANAGE, AccessContext.course(link.courseId()));
        links.revoke(actor.tenantId(), linkId, clock.instant());
        changes.audit(actor.tenantId(), actor.userId(), EnrollmentChanges.OBJECT_INVITE_LINK, linkId, "revoked",
                link.courseId(), null);
    }
}
