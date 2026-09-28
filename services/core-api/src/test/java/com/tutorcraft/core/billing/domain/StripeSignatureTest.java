package com.tutorcraft.core.billing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class StripeSignatureTest {

    private static final String SECRET = "whsec_test_secret";
    private static final byte[] PAYLOAD = "{\"id\":\"evt_1\",\"type\":\"checkout.session.completed\"}".getBytes(StandardCharsets.UTF_8);
    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");
    private static final Duration TOLERANCE = StripeSignature.DEFAULT_TOLERANCE;

    @Test
    void validSignatureIsAccepted() {
        String header = StripeSignature.sign(SECRET, NOW.getEpochSecond(), PAYLOAD);

        assertThat(StripeSignature.verify(header, PAYLOAD, SECRET, NOW, TOLERANCE)).isTrue();
    }

    @Test
    void knownVectorMatchesHmacSha256OfTimestampDotPayload() {
        // HMAC-SHA256(key "secret", "1700000000.{}"), посчитано независимо (python hmac)
        String header = "t=1700000000,v1=b8569b78799ff9e3cbff0fc2d63a33a2b57f3282abd07c37ae5e8e7d79a5f163";
        byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);

        assertThat(StripeSignature.verify(header, payload, "secret", Instant.ofEpochSecond(1_700_000_000L), TOLERANCE)).isTrue();
        assertThat(StripeSignature.sign("secret", 1_700_000_000L, payload)).isEqualTo(header);
    }

    @Test
    void anyOfMultipleSignaturesMayMatch() {
        String valid = StripeSignature.sign(SECRET, NOW.getEpochSecond(), PAYLOAD);
        String header = valid.replace(",v1=", ",v1=00ff,v0=abc,v1=");

        assertThat(StripeSignature.verify(header, PAYLOAD, SECRET, NOW, TOLERANCE)).isTrue();
    }

    @Test
    void tamperedPayloadOrWrongSecretIsRejected() {
        String header = StripeSignature.sign(SECRET, NOW.getEpochSecond(), PAYLOAD);

        assertThat(StripeSignature.verify(header, "{}".getBytes(StandardCharsets.UTF_8), SECRET, NOW, TOLERANCE)).isFalse();
        assertThat(StripeSignature.verify(header, PAYLOAD, "whsec_other", NOW, TOLERANCE)).isFalse();
    }

    @Test
    void timestampOutsideToleranceIsRejected() {
        String old = StripeSignature.sign(SECRET, NOW.minusSeconds(301).getEpochSecond(), PAYLOAD);
        String edge = StripeSignature.sign(SECRET, NOW.minusSeconds(300).getEpochSecond(), PAYLOAD);
        String future = StripeSignature.sign(SECRET, NOW.plusSeconds(301).getEpochSecond(), PAYLOAD);

        assertThat(StripeSignature.verify(old, PAYLOAD, SECRET, NOW, TOLERANCE)).isFalse();
        assertThat(StripeSignature.verify(edge, PAYLOAD, SECRET, NOW, TOLERANCE)).isTrue();
        assertThat(StripeSignature.verify(future, PAYLOAD, SECRET, NOW, TOLERANCE)).isFalse();
    }

    @Test
    void malformedHeadersAreRejected() {
        assertThat(StripeSignature.verify(null, PAYLOAD, SECRET, NOW, TOLERANCE)).isFalse();
        assertThat(StripeSignature.verify("garbage", PAYLOAD, SECRET, NOW, TOLERANCE)).isFalse();
        assertThat(StripeSignature.verify("t=abc,v1=zz", PAYLOAD, SECRET, NOW, TOLERANCE)).isFalse();
        assertThat(StripeSignature.verify("t=" + NOW.getEpochSecond(), PAYLOAD, SECRET, NOW, TOLERANCE)).isFalse();
        assertThat(StripeSignature.verify("v1=00", PAYLOAD, SECRET, NOW, TOLERANCE)).isFalse();
        assertThat(StripeSignature.verify(StripeSignature.sign(SECRET, NOW.getEpochSecond(), PAYLOAD), PAYLOAD, "", NOW,
                TOLERANCE)).isFalse();
    }
}
