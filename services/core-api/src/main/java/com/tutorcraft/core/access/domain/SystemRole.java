package com.tutorcraft.core.access.domain;

import static com.tutorcraft.core.access.domain.Permission.*;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Системные неизменяемые роли (ТЗ 3.2, docs/permissions.md). Администрирование — только у {@code platform_admin}
 * (единственный главный администратор из env, см. PlatformAdminBootstrapper); {@code tenant_admin} — владелец школы
 * (репетитор): ведёт свои курсы и учеников своей школы ({@code member.view/manage}), но админ-разделы ему недоступны.
 */
public enum SystemRole {
    PLATFORM_ADMIN("platform_admin", RoleScope.PLATFORM, EnumSet.allOf(Permission.class)),
    TENANT_ADMIN("tenant_admin", RoleScope.TENANT, allExcept(adminOnly())),
    CATEGORY_MANAGER("category_manager", RoleScope.CATEGORY, EnumSet.of(
            COURSE_CREATE, COURSE_VIEW, COURSE_VIEW_HIDDEN, COURSE_EDIT, COURSE_DELETE, COURSE_PUBLISH,
            CONTENT_VIEW, ENROLLMENT_VIEW, ENROLLMENT_MANAGE, GROUP_MANAGE, REPORT_VIEW, FILE_UPLOAD)),
    TEACHER("teacher", RoleScope.COURSE, EnumSet.of(
            COURSE_VIEW, COURSE_VIEW_HIDDEN, COURSE_EDIT, COURSE_DELETE, COURSE_PUBLISH, CONTENT_VIEW,
            ENROLLMENT_VIEW, ENROLLMENT_MANAGE, GROUP_MANAGE, SUBMISSION_VIEW_ALL, SUBMISSION_GRADE,
            GRADE_VIEW_ALL, GRADE_EDIT, GRADE_PUBLISH, GRADE_EXPORT, GRADEBOOK_CONFIGURE,
            QUIZ_MANAGE, QUIZ_VIEW_REPORTS, QBANK_MANAGE, FORUM_POST, FORUM_MODERATE, FORUM_ANNOUNCE,
            COMPLETION_VIEW_ALL, REPORT_VIEW, FILE_UPLOAD)),
    ASSISTANT("assistant", RoleScope.COURSE, EnumSet.of(
            COURSE_VIEW, COURSE_VIEW_HIDDEN, CONTENT_VIEW, ENROLLMENT_VIEW, SUBMISSION_VIEW_ALL, SUBMISSION_GRADE,
            GRADE_VIEW_ALL, GRADE_EDIT, QUIZ_VIEW_REPORTS, FORUM_POST, FORUM_MODERATE, COMPLETION_VIEW_ALL, FILE_UPLOAD)),
    STUDENT("student", RoleScope.COURSE, EnumSet.of(
            COURSE_VIEW, CONTENT_VIEW, SUBMISSION_SUBMIT, GRADE_VIEW_OWN, QUIZ_ATTEMPT, FORUM_POST, FILE_UPLOAD)),
    OBSERVER("observer", RoleScope.COURSE, EnumSet.of(COURSE_VIEW, CONTENT_VIEW, GRADE_VIEW_ALL, COMPLETION_VIEW_ALL)),
    GUEST("guest", RoleScope.COURSE, EnumSet.of(COURSE_VIEW, CONTENT_VIEW));

    private final String key;
    private final RoleScope scope;
    private final Set<Permission> permissions;

    SystemRole(String key, RoleScope scope, Set<Permission> permissions) {
        this.key = key;
        this.scope = scope;
        this.permissions = Set.copyOf(permissions);
    }

    public String key() {
        return key;
    }

    public RoleScope scope() {
        return scope;
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    public static Optional<SystemRole> fromKey(String key) {
        return Arrays.stream(values()).filter(role -> role.key.equals(key)).findFirst();
    }

    /** Права админ-разделов (пользователи, категории, брендинг, аудит, заказы, интеграции). */
    public static Set<Permission> adminOnly() {
        return EnumSet.of(PLATFORM_MANAGE, TENANT_MANAGE, TENANT_BRANDING, USER_VIEW, USER_MANAGE, USER_IMPORT,
                USER_IMPERSONATE, ROLE_MANAGE, CATEGORY_MANAGE, AUDIT_VIEW, INTEGRATION_MANAGE, BILLING_MANAGE);
    }

    private static Set<Permission> allExcept(Set<Permission> excluded) {
        EnumSet<Permission> all = EnumSet.allOf(Permission.class);
        all.removeAll(excluded);
        return all;
    }
}
