package com.tutorcraft.core.activity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PathParamsTest {

    @Test
    void keepsObjectIdsAndMasksSecrets() {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("courseId", "c-1");
        variables.put("token", "secret-value");
        assertThat(PathParams.sanitize(variables)).containsEntry("courseId", "c-1").containsEntry("token", "***");
    }

    @Test
    void masksSecretInActualPath() {
        Map<String, String> variables = Map.of("token", "abcdef123");
        assertThat(PathParams.maskPath("/api/v1/calendar/ical/abcdef123.ics", variables))
                .isEqualTo("/api/v1/calendar/ical/***.ics");
    }

    @Test
    void sensitiveNamesAreCaseInsensitive() {
        assertThat(PathParams.isSensitive("inviteCode")).isTrue();
        assertThat(PathParams.isSensitive("ApiKeyId")).isTrue();
        assertThat(PathParams.isSensitive("itemId")).isFalse();
    }

    @Test
    void emptyInputIsSafe() {
        assertThat(PathParams.sanitize(null)).isEmpty();
        assertThat(PathParams.maskPath("/api/v1/me", null)).isEqualTo("/api/v1/me");
    }
}
