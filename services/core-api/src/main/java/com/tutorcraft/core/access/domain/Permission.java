package com.tutorcraft.core.access.domain;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Полный перечень разрешений (docs/permissions.md). Ключ — формат {@code ресурс.действие}. */
public enum Permission {
    PLATFORM_MANAGE("platform.manage"),
    TENANT_MANAGE("tenant.manage"),
    TENANT_BRANDING("tenant.branding"),
    USER_VIEW("user.view"),
    USER_MANAGE("user.manage"),
    USER_IMPORT("user.import"),
    USER_IMPERSONATE("user.impersonate"),
    ROLE_MANAGE("role.manage"),
    MEMBER_VIEW("member.view"),
    MEMBER_MANAGE("member.manage"),
    CATEGORY_MANAGE("category.manage"),
    COURSE_CREATE("course.create"),
    COURSE_VIEW("course.view"),
    COURSE_VIEW_HIDDEN("course.viewHidden"),
    COURSE_EDIT("course.edit"),
    COURSE_DELETE("course.delete"),
    COURSE_PUBLISH("course.publish"),
    CONTENT_VIEW("content.view"),
    ENROLLMENT_VIEW("enrollment.view"),
    ENROLLMENT_MANAGE("enrollment.manage"),
    GROUP_MANAGE("group.manage"),
    SUBMISSION_SUBMIT("submission.submit"),
    SUBMISSION_VIEW_ALL("submission.viewAll"),
    SUBMISSION_GRADE("submission.grade"),
    GRADE_VIEW_OWN("grade.viewOwn"),
    GRADE_VIEW_ALL("grade.viewAll"),
    GRADE_EDIT("grade.edit"),
    GRADE_PUBLISH("grade.publish"),
    GRADE_EXPORT("grade.export"),
    GRADEBOOK_CONFIGURE("gradebook.configure"),
    QUIZ_ATTEMPT("quiz.attempt"),
    QUIZ_MANAGE("quiz.manage"),
    QUIZ_VIEW_REPORTS("quiz.viewReports"),
    QBANK_MANAGE("qbank.manage"),
    FORUM_POST("forum.post"),
    FORUM_MODERATE("forum.moderate"),
    FORUM_ANNOUNCE("forum.announce"),
    COMPLETION_VIEW_ALL("completion.viewAll"),
    REPORT_VIEW("report.view"),
    AUDIT_VIEW("audit.view"),
    INTEGRATION_MANAGE("integration.manage"),
    BILLING_MANAGE("billing.manage"),
    FILE_UPLOAD("file.upload");

    private static final Map<String, Permission> BY_KEY = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(Permission::key, Function.identity()));

    private final String key;

    Permission(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<Permission> fromKey(String key) {
        return Optional.ofNullable(BY_KEY.get(key));
    }
}
