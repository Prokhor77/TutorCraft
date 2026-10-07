package com.tutorcraft.core.billing.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tutorcraft.core.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Сроки доступа школ у главного администратора: список и ручная установка конца бесплатного доступа. */
class PlatformSubscriptionsIT extends IntegrationTest {

    private static final String API = "/api/v1";
    private static final String PLATFORM = API + "/platform/subscriptions";

    private UUID platformTenant;
    private UUID platformAdmin;
    private UUID tenant;
    private UUID owner;

    @BeforeEach
    void setUp() {
        platformTenant = createTenant("platform");
        platformAdmin = createPlatformAdmin(platformTenant, unique("root"));
        tenant = createTenant("school");
        owner = createUser(tenant, unique("owner"));
        grantTenantRole(tenant, owner, "tenant_admin");
    }

    @Test
    void adminExtendsExpiredTrialAndSchoolCanCreateCoursesAgain() throws Exception {
        expireTrial();
        createCourse().andExpect(status().isUnprocessableEntity());
        Instant until = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

        mvc.perform(get(PLATFORM).header(HttpHeaders.AUTHORIZATION, admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.tenantId == '" + tenant + "')].status").value("expired"));
        setTrialEnd(tenant, until, 1)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("trial"))
            .andExpect(jsonPath("$.trialEndsAt").value(until.toString()))
            .andExpect(jsonPath("$.version").value(2));

        createCourse().andExpect(status().isCreated());
        Integer audits = jdbc.sql("""
                SELECT count(*) FROM audit_log WHERE tenant_id = :tenantId AND action = 'subscription.trial_changed'
                """).param("tenantId", tenant).query(Integer.class).single();
        Assertions.assertThat(audits).isEqualTo(1);
    }

    @Test
    void staleVersionAndPastDateAreRejected() throws Exception {
        expireTrial();
        setTrialEnd(tenant, Instant.now().plus(7, ChronoUnit.DAYS), 0)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("conflict.version"));
        setTrialEnd(tenant, Instant.now().minus(1, ChronoUnit.DAYS), 1)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("billing.trial_end_out_of_range"));
    }

    @Test
    void schoolOwnerCannotUsePlatformEndpoints() throws Exception {
        mvc.perform(get(PLATFORM).header(HttpHeaders.AUTHORIZATION, bearer(tenant, owner)))
            .andExpect(status().isForbidden());
        mvc.perform(put(PLATFORM + "/" + tenant + "/trial").header(HttpHeaders.AUTHORIZATION, bearer(tenant, owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Instant.now().plus(365, ChronoUnit.DAYS), 0)))
            .andExpect(status().isForbidden());
    }

    @Test
    void tenantListShowsWhoRegisteredTheSchool() throws Exception {
        mvc.perform(get(API + "/platform/tenants").header(HttpHeaders.AUTHORIZATION, admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == '" + tenant + "')].owner.id").value(owner.toString()));
    }

    private void expireTrial() throws Exception {
        mvc.perform(get(API + "/billing/subscription").header(HttpHeaders.AUTHORIZATION, bearer(tenant, owner)))
            .andExpect(status().isOk());
        jdbc.sql("""
                UPDATE tenant_subscriptions SET trial_ends_at = :past, version = version + 1 WHERE tenant_id = :tenantId
                """)
            .param("past", Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS))).param("tenantId", tenant)
            .update();
    }

    private ResultActions setTrialEnd(UUID tenantId, Instant until, long version) throws Exception {
        return mvc.perform(put(PLATFORM + "/" + tenantId + "/trial").header(HttpHeaders.AUTHORIZATION, admin())
                .contentType(MediaType.APPLICATION_JSON).content(body(until, version)));
    }

    private ResultActions createCourse() throws Exception {
        return mvc.perform(post(API + "/courses").header(HttpHeaders.AUTHORIZATION, bearer(tenant, owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Algebra\"}"));
    }

    private String admin() {
        return platformAdminBearer(platformTenant, platformAdmin);
    }

    private static String body(Instant until, long version) {
        return "{\"trialEndsAt\":\"" + until + "\",\"version\":" + version + "}";
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.test";
    }
}
