package com.tutorcraft.core.communication.forum.application;

import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.communication.NotificationCategory;
import com.tutorcraft.core.communication.NotificationsApi;
import com.tutorcraft.core.communication.NotificationsApi.NotificationCommand;
import com.tutorcraft.core.communication.forum.application.ForumItems.ForumContext;
import com.tutorcraft.core.communication.forum.domain.Discussion;
import com.tutorcraft.core.communication.forum.domain.ForumPost;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.enrollment.EnrollmentApi.Member;
import com.tutorcraft.core.shared.domain.ValidationException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Уведомления форума (FR-FORUM-02/03, FR-NOTIF-01): объявление — всем активным студентам курса;
 * ответ — подписчикам темы, кроме автора; упоминание — упомянутым участникам курса. Автор не уведомляется никогда.
 */
@Component
public class ForumNotifier {

    static final int MAX_MENTIONS = 20;
    private static final String ACTIVE = "active";
    private static final String ANNOUNCEMENT_CODE = "forum.announcement";
    private static final String REPLY_CODE = "forum.reply";
    private static final String MENTION_CODE = "forum.mention";

    private final NotificationsApi notifications;
    private final EnrollmentApi enrollment;

    public ForumNotifier(NotificationsApi notifications, EnrollmentApi enrollment) {
        this.notifications = notifications;
        this.enrollment = enrollment;
    }

    /** Упомянутые активные участники курса без автора. @throws ValidationException слишком много упоминаний */
    public Set<UUID> resolveMentions(ForumContext context, Collection<UUID> requested) {
        if (requested == null || requested.isEmpty()) {
            return Set.of();
        }
        Set<UUID> unique = new LinkedHashSet<>(requested);
        unique.remove(context.userId());
        if (unique.size() > MAX_MENTIONS) {
            throw ValidationException.single("mentions", "too_many", "At most " + MAX_MENTIONS + " mentions are allowed");
        }
        return unique.stream().filter(userId -> enrollment.membership(context.tenantId(), context.courseId(), userId)
                .filter(member -> ACTIVE.equals(member.status())).isPresent()).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public void announcement(ForumContext context, Discussion discussion, String authorName) {
        Set<UUID> students = enrollment.activeMembers(context.tenantId(), context.courseId(), Set.of(CourseRole.STUDENT))
                .stream().map(Member::userId).filter(id -> !id.equals(discussion.authorId())).collect(Collectors.toSet());
        send(context, students, NotificationCategory.ANNOUNCEMENT, ANNOUNCEMENT_CODE, discussion, authorName,
                "forum-announcement:" + discussion.id());
    }

    /** Ответ: подписчикам (без автора и упомянутых — им уходит отдельное уведомление), затем упомянутым. */
    public void reply(ForumContext context, Discussion discussion, ForumPost post, Set<UUID> subscribers, Set<UUID> mentioned,
                      String authorName) {
        Set<UUID> recipients = subscribers.stream()
                .filter(id -> !id.equals(post.authorId()) && !mentioned.contains(id)).collect(Collectors.toSet());
        send(context, recipients, NotificationCategory.FORUM_REPLY, REPLY_CODE, discussion, authorName,
                "forum-reply:" + post.id());
        mentions(context, discussion, post, mentioned, authorName);
    }

    public void mentions(ForumContext context, Discussion discussion, ForumPost post, Set<UUID> mentioned, String authorName) {
        send(context, mentioned, NotificationCategory.FORUM_REPLY, MENTION_CODE, discussion, authorName,
                "forum-mention:" + post.id());
    }

    private void send(ForumContext context, Set<UUID> recipients, NotificationCategory category, String code,
                      Discussion discussion, String authorName, String dedupeKey) {
        if (recipients.isEmpty()) {
            return;
        }
        notifications.notify(NotificationCommand.of(context.tenantId(), recipients, category, code,
                List.of(discussion.title(), authorName, context.item().title()), link(discussion), dedupeKey));
    }

    static String link(Discussion discussion) {
        return "/courses/" + discussion.courseId() + "/items/" + discussion.itemId() + "/discussions/" + discussion.id();
    }
}
