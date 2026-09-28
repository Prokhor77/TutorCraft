package com.tutorcraft.core.integrations.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WebhookSignatureTest {

    private static final String SECRET = "whsec_test-secret";
    private static final long TIMESTAMP = 1_790_000_000L;
    private static final String BODY = "{\"id\":\"1\",\"event\":\"grade.published\"}";

    @Test
    void headerHasTimestampAndHexHmacOfTimestampDotBody() throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = HexFormat.of().formatHex(mac.doFinal((TIMESTAMP + "." + BODY).getBytes(StandardCharsets.UTF_8)));

        assertThat(WebhookSignature.header(SECRET, TIMESTAMP, BODY)).isEqualTo("t=" + TIMESTAMP + ",v1=" + expected);
    }

    @Test
    void signatureDependsOnBodySecretAndTimestamp() {
        String original = WebhookSignature.header(SECRET, TIMESTAMP, BODY);

        assertThat(WebhookSignature.header(SECRET, TIMESTAMP, BODY + " ")).isNotEqualTo(original);
        assertThat(WebhookSignature.header("other", TIMESTAMP, BODY)).isNotEqualTo(original);
        assertThat(WebhookSignature.header(SECRET, TIMESTAMP + 1, BODY)).isNotEqualTo(original);
    }

    @Test
    void hexDigestIs64LowercaseCharacters() {
        assertThat(WebhookSignature.hmacHex(SECRET, BODY)).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void retryScheduleDoublesFromThirtySecondsAndStopsAtEightAttempts() {
        assertThat(RetrySchedule.delayAfter(1)).contains(java.time.Duration.ofSeconds(30));
        assertThat(RetrySchedule.delayAfter(2)).contains(java.time.Duration.ofSeconds(60));
        assertThat(RetrySchedule.delayAfter(7)).contains(java.time.Duration.ofSeconds(30L * 64));
        assertThat(RetrySchedule.delayAfter(RetrySchedule.MAX_ATTEMPTS)).isEmpty();
    }
}
