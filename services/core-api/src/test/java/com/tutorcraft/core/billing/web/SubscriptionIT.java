package com.tutorcraft.core.billing.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Подписка школы: пробный период, режим «только чтение» после окончания, мгновенная fake-покупка срока. */
class SubscriptionIT extends IntegrationTest {

    private static final String API = "/api/v1";
    private static final String SUBSCRIPTION = API + "/billing/subscription";
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private UUID tenant;
    private UUID owner;
    private UUID student;

    @BeforeEach
    void setUp() {
        tenant = createTenant("subscription");
        owner = createUser(tenant, unique("owner"));
        grantTenantRole(tenant, owner, "tenant_admin");
        student = createUser(tenant, unique("student"));
    }

    @Test
    void newSchoolIsOnTrialAndCanCreateCourses() throws Exception {
        perform(get(SUBSCRIPTION), owner)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("trial"))
            .andExpect(jsonPath("$.canManage").value(true))
            .andExpect(jsonPath("$.terms.length()").value(3))
            .andExpect(jsonPath("$.terms[2].price.amountMinor").value(24000))
            .andExpect(jsonPath("$.terms[2].price.currency").value("USD"));

        createCourse().andExpect(status().isCreated());
    }

    @Test
    void expiredSchoolCannotCreateOrPublishCourses() throws Exception {
        String courseId = JsonPath.read(createCourse().andReturn().getResponse().getContentAsString(), "$.id");
        expireTrial();

        perform(get(SUBSCRIPTION), owner).andExpect(jsonPath("$.status").value("expired"));
        createCourse()
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("billing.subscription_inactive"));
        perform(patch(API + "/courses/" + courseId).header(HttpHeaders.IF_MATCH, "\"0\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"published\"}"), owner)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("billing.subscription_inactive"));
    }

    @Test
    void purchaseActivatesImmediatelyAndRepeatedKeyDoesNotExtendTwice() throws Exception {
        expireTrial();
        String key = UUID.randomUUID().toString();

        String first = purchase("year", key)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("active"))
            .andExpect(jsonPath("$.payments.length()").value(1))
            .andExpect(jsonPath("$.payments[0].amount.amountMinor").value(24000))
            .andReturn().getResponse().getContentAsString();
        String repeated = purchase("year", key).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        String paidUntil = JsonPath.read(first, "$.paidUntil");
        Assertions.assertThat((String) JsonPath.read(repeated, "$.paidUntil")).isEqualTo(paidUntil);
        Assertions.assertThat(Instant.parse(paidUntil)).isAfter(Instant.now().plus(360, ChronoUnit.DAYS));
        createCourse().andExpect(status().isCreated());
    }

    @Test
    void studentCannotSeeOrBuySubscription() throws Exception {
        perform(get(SUBSCRIPTION), student).andExpect(status().isForbidden());
        purchaseAs(student, "month", UUID.randomUUID().toString()).andExpect(status().isForbidden());
    }

    @Test
    void unknownTermIsRejected() throws Exception {
        purchase("week", UUID.randomUUID().toString()).andExpect(status().isBadRequest());
    }

    private void expireTrial() throws Exception {
        perform(get(SUBSCRIPTION), owner).andExpect(status().isOk());
        jdbc.sql("UPDATE tenant_subscriptions SET trial_ends_at = :past WHERE tenant_id = :tenantId")
            .param("past", Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS))).param("tenantId", tenant)
            .update();
    }

    private ResultActions createCourse() throws Exception {
        return perform(post(API + "/courses").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Algebra\"}"), owner);
    }

    private ResultActions purchase(String term, String key) throws Exception {
        return purchaseAs(owner, term, key);
    }

    private ResultActions purchaseAs(UUID userId, String term, String key) throws Exception {
        return perform(post(SUBSCRIPTION + "/purchases").header(IDEMPOTENCY_KEY, key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"term\":\"" + term + "\"}"), userId);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, UUID userId) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(tenant, userId)));
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.test";
    }
}
