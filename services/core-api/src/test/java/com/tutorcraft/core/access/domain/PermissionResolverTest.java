package com.tutorcraft.core.access.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PermissionResolverTest {

    private static final Map<String, Set<Permission>> SYSTEM = Arrays.stream(SystemRole.values())
            .collect(Collectors.toMap(SystemRole::key, SystemRole::permissions));
    private final PermissionResolver resolver = new PermissionResolver(SYSTEM);

    @Test
    void studentGetsOnlyStudentPermissionsInCourse() {
        Set<Permission> result = resolver.resolve(List.of(), List.of(), Optional.of("student"));

        assertThat(result).contains(Permission.SUBMISSION_SUBMIT, Permission.QUIZ_ATTEMPT)
                .doesNotContain(Permission.COURSE_EDIT, Permission.SUBMISSION_GRADE);
    }

    @Test
    void schoolOwnerRunsCoursesButHasNoAdministration() {
        Set<Permission> result = resolver.resolve(List.of(new RoleGrant("tenant_admin", "tenant", null)), List.of(), Optional.empty());

        assertThat(result).contains(Permission.COURSE_CREATE, Permission.COURSE_EDIT, Permission.GRADE_EDIT)
                .doesNotContainAnyElementsOf(SystemRole.adminOnly());
    }

    @Test
    void platformAdminHasEverything() {
        Set<Permission> result = resolver.resolve(List.of(new RoleGrant("platform_admin", "platform", null)), List.of(), Optional.empty());

        assertThat(result).containsAll(SystemRole.adminOnly()).contains(Permission.COURSE_EDIT);
    }

    @Test
    void categoryManagerAppliesToDescendantCategoriesOnly() {
        UUID parent = UUID.randomUUID();
        UUID child = UUID.randomUUID();
        RoleGrant grant = new RoleGrant("category_manager", "category", parent);

        assertThat(resolver.resolve(List.of(grant), List.of(child, parent), Optional.empty())).contains(Permission.COURSE_EDIT);
        assertThat(resolver.resolve(List.of(grant), List.of(UUID.randomUUID()), Optional.empty())).isEmpty();
    }

    @Test
    void noGrantsAndNoEnrollmentMeansNoPermissions() {
        assertThat(resolver.resolve(List.of(), List.of(), Optional.empty())).isEmpty();
    }

    @Test
    void unknownRoleKeyGrantsNothing() {
        assertThat(resolver.resolve(List.of(), List.of(), Optional.of("ghost"))).isEmpty();
    }

    @Test
    void teacherAndAssistantDiffer() {
        Set<Permission> assistant = resolver.resolve(List.of(), List.of(), Optional.of("assistant"));

        assertThat(assistant).contains(Permission.SUBMISSION_GRADE).doesNotContain(Permission.COURSE_EDIT, Permission.GRADEBOOK_CONFIGURE);
    }
}
