package com.tutorcraft.core.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class TelegramLoginVerifierTest {

    private static final String BOT_TOKEN = "123456:ABC-test-bot-token";
    private static final Instant NOW = Instant.parse("2026-09-27T18:00:00Z");
    private static final Duration MAX_AGE = Duration.ofDays(1);

    private final TelegramLoginVerifier verifier = new TelegramLoginVerifier(BOT_TOKEN, MAX_AGE);

    @Test
    void dataCheckStringIsSortedKeyValueLinesWithoutHash() {
        Map<String, String> fields = Map.of("username", "ivan", "id", "42", "auth_date", "1700000000", "hash", "x",
                "first_name", "Ivan");

        assertThat(TelegramLoginVerifier.dataCheckString(fields))
                .isEqualTo("auth_date=1700000000\nfirst_name=Ivan\nid=42\nusername=ivan");
    }

    @Test
    void acceptsCorrectlySignedFreshData() {
        Map<String, String> fields = signed(widgetFields(NOW.minusSeconds(60)));

        assertThat(verifier.verify(fields, NOW)).isEqualTo(TelegramLoginVerifier.Result.VALID);
    }

    @Test
    void acceptsUppercaseHexHash() {
        Map<String, String> fields = signed(widgetFields(NOW.minusSeconds(60)));
        fields.put("hash", fields.get("hash").toUpperCase());

        assertThat(verifier.verify(fields, NOW)).isEqualTo(TelegramLoginVerifier.Result.VALID);
    }

    @Test
    void rejectsTamperedField() {
        Map<String, String> fields = signed(widgetFields(NOW.minusSeconds(60)));
        fields.put("id", "43");

        assertThat(verifier.verify(fields, NOW)).isEqualTo(TelegramLoginVerifier.Result.BAD_SIGNATURE);
    }

    @Test
    void rejectsDataSignedWithAnotherBotToken() {
        Map<String, String> fields = signedWith("999:other-token", widgetFields(NOW.minusSeconds(60)));

        assertThat(verifier.verify(fields, NOW)).isEqualTo(TelegramLoginVerifier.Result.BAD_SIGNATURE);
    }

    @Test
    void rejectsStaleAuthDate() {
        Map<String, String> fields = signed(widgetFields(NOW.minus(MAX_AGE).minusSeconds(1)));

        assertThat(verifier.verify(fields, NOW)).isEqualTo(TelegramLoginVerifier.Result.EXPIRED);
    }

    @Test
    void rejectsMissingHashOrAuthDate() {
        Map<String, String> noHash = widgetFields(NOW);
        Map<String, String> badDate = signed(widgetFields(NOW));
        badDate.put("auth_date", "not-a-number");

        assertThat(verifier.verify(noHash, NOW)).isEqualTo(TelegramLoginVerifier.Result.MALFORMED);
        assertThat(verifier.verify(badDate, NOW)).isEqualTo(TelegramLoginVerifier.Result.MALFORMED);
    }

    @Test
    void requiresBotToken() {
        assertThatThrownBy(() -> new TelegramLoginVerifier(" ", MAX_AGE)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Map<String, String> widgetFields(Instant authDate) {
        Map<String, String> fields = new HashMap<>();
        fields.put("id", "42");
        fields.put("first_name", "Иван");
        fields.put("username", "ivan");
        fields.put("auth_date", String.valueOf(authDate.getEpochSecond()));
        return fields;
    }

    private static Map<String, String> signed(Map<String, String> fields) {
        return signedWith(BOT_TOKEN, fields);
    }

    /** Независимая реализация алгоритма из документации Telegram. */
    private static Map<String, String> signedWith(String botToken, Map<String, String> fields) {
        try {
            byte[] secret = MessageDigest.getInstance("SHA-256").digest(botToken.getBytes(StandardCharsets.UTF_8));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            String checkString = fields.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getKey() + "=" + e.getValue()).reduce((a, b) -> a + "\n" + b).orElse("");
            Map<String, String> result = new HashMap<>(fields);
            result.put("hash", HexFormat.of().formatHex(mac.doFinal(checkString.getBytes(StandardCharsets.UTF_8))));
            return result;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
