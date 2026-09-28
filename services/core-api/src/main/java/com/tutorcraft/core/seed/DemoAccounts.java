package com.tutorcraft.core.seed;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.TenantRole;
import com.tutorcraft.core.identity.application.AccountProvisioner;
import com.tutorcraft.core.identity.application.AccountProvisioner.Signup;
import com.tutorcraft.core.org.OrgApi;
import com.tutorcraft.core.org.OrgApi.TenantInfo;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

/** Демо-tenant, преподаватель-администратор и студенты (вызывать в транзакции). */
@Component
class DemoAccounts {

    static final int STUDENT_COUNT = 20;
    static final String TEACHER_EMAIL = "teacher@demo.local";
    private static final String TENANT_NAME = "Демо-школа TutorCraft";
    private static final String STUDENT_EMAIL_TEMPLATE = "student%02d@demo.local";
    private static final String STUDENT_FIRST_NAME = "Студент";
    private static final String STUDENT_LAST_NAME_TEMPLATE = "Демо %02d";

    private final OrgApi org;
    private final AccountProvisioner provisioner;
    private final AccessService access;

    DemoAccounts(OrgApi org, AccountProvisioner provisioner, AccessService access) {
        this.org = org;
        this.provisioner = provisioner;
        this.access = access;
    }

    Accounts create(String tenantSlug, String password) {
        TenantInfo tenant = org.createTenant(TENANT_NAME, tenantSlug);
        if (!tenantSlug.equals(tenant.slug())) {
            throw new IllegalStateException("Demo tenant slug is taken");
        }
        UUID teacherId = provisioner.createMember(tenant.id(),
                new Signup(TEACHER_EMAIL, password, "Анна", "Преподавателева", null, null, null)).id();
        access.replaceTenantRoles(tenant.id(), teacherId, Set.of(TenantRole.TENANT_ADMIN), teacherId);
        List<UUID> students = IntStream.rangeClosed(1, STUDENT_COUNT)
                .mapToObj(number -> provisioner.createMember(tenant.id(), studentSignup(number, password)).id())
                .toList();
        return new Accounts(tenant.id(), teacherId, students);
    }

    private static Signup studentSignup(int number, String password) {
        return new Signup(STUDENT_EMAIL_TEMPLATE.formatted(number), password, STUDENT_FIRST_NAME,
                STUDENT_LAST_NAME_TEMPLATE.formatted(number), null, null, null);
    }

    record Accounts(UUID tenantId, UUID teacherId, List<UUID> studentIds) {
    }
}
