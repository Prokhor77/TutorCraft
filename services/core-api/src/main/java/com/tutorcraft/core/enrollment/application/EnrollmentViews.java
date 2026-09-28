package com.tutorcraft.core.enrollment.application;

import com.tutorcraft.core.enrollment.application.EnrollmentView.UserView;
import com.tutorcraft.core.enrollment.domain.Enrollment;
import com.tutorcraft.core.files.FilesApi;
import com.tutorcraft.core.files.FilesApi.FileRef;
import com.tutorcraft.core.identity.UsersApi;
import com.tutorcraft.core.identity.UsersApi.UserRef;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Сборка Enrollment: данные пользователя (UsersApi), аватар (FilesApi), группы — пакетно на страницу. */
@Component
class EnrollmentViews {

    private static final String READY = "ready";

    private final UsersApi users;
    private final FilesApi files;
    private final GroupRepository groups;

    EnrollmentViews(UsersApi users, FilesApi files, GroupRepository groups) {
        this.users = users;
        this.files = files;
        this.groups = groups;
    }

    List<EnrollmentView> views(UUID tenantId, UUID courseId, List<Enrollment> enrollments) {
        if (enrollments.isEmpty()) {
            return List.of();
        }
        List<UUID> userIds = enrollments.stream().map(Enrollment::userId).toList();
        Map<UUID, UserRef> userRefs = users.findAll(tenantId, userIds);
        Map<UUID, String> avatars = avatarUrls(tenantId, userRefs.values());
        Map<UUID, List<UUID>> groupIds = groups.groupIdsOfUsers(tenantId, courseId, userIds);
        return enrollments.stream()
                .map(enrollment -> toView(enrollment, userRefs.get(enrollment.userId()), avatars,
                        groupIds.getOrDefault(enrollment.userId(), List.of())))
                .toList();
    }

    EnrollmentView view(Enrollment enrollment) {
        return views(enrollment.tenantId(), enrollment.courseId(), List.of(enrollment)).get(0);
    }

    private Map<UUID, String> avatarUrls(UUID tenantId, Collection<UserRef> refs) {
        List<UUID> fileIds = refs.stream().map(UserRef::avatarFileId).filter(Objects::nonNull).distinct().toList();
        return files.findAll(tenantId, fileIds).stream()
                .filter(file -> READY.equals(file.status()))
                .collect(Collectors.toMap(FileRef::id, files::downloadUrl));
    }

    private static EnrollmentView toView(Enrollment enrollment, UserRef user, Map<UUID, String> avatars, List<UUID> groupIds) {
        UserView userView = user == null
                ? new UserView(enrollment.userId(), "", "", "", null)
                : new UserView(user.id(), user.firstName(), user.lastName(), user.email(),
                        user.avatarFileId() == null ? null : avatars.get(user.avatarFileId()));
        return new EnrollmentView(enrollment.id(), userView, enrollment.role().key(), enrollment.status().key(),
                enrollment.method(), enrollment.startsAt(), enrollment.endsAt(), groupIds, enrollment.lastAccessAt());
    }
}
