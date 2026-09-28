package com.tutorcraft.core.identity.domain;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Проверка данных Telegram Login Widget по официальному алгоритму
 * (https://core.telegram.org/widgets/login#checking-authorization):
 * data_check_string = отсортированные строки "key=value" всех полей, кроме hash, через '\n';
 * secret_key = SHA256(bot_token); hash == hex(HMAC_SHA256(data_check_string, secret_key)).
 */
public final class TelegramLoginVerifier {

    public static final String HASH_FIELD = "hash";
    public static final String AUTH_DATE_FIELD = "auth_date";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String DIGEST_ALGORITHM = "SHA-256";
    private static final String LINE_SEPARATOR = "\n";
    private static final String KEY_VALUE_SEPARATOR = "=";

    public enum Result { VALID, BAD_SIGNATURE, EXPIRED, MALFORMED }

    private final byte[] secretKey;
    private final Duration maxAge;

    public TelegramLoginVerifier(String botToken, Duration maxAge) {
        if (botToken == null || botToken.isBlank()) {
            throw new IllegalArgumentException("Telegram bot token is required");
        }
        this.secretKey = sha256(botToken.getBytes(StandardCharsets.UTF_8));
        this.maxAge = maxAge;
    }

    /** @param fields все поля, полученные от виджета (включая hash), без null-значений */
    public Result verify(Map<String, String> fields, Instant now) {
        String hash = fields.get(HASH_FIELD);
        Long authDate = parseLong(fields.get(AUTH_DATE_FIELD));
        if (hash == null || authDate == null) {
            return Result.MALFORMED;
        }
        byte[] expected = hmacHex(dataCheckString(fields)).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = hash.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, actual)) {
            return Result.BAD_SIGNATURE;
        }
        Instant issuedAt = Instant.ofEpochSecond(authDate);
        return issuedAt.plus(maxAge).isBefore(now) ? Result.EXPIRED : Result.VALID;
    }

    public static String dataCheckString(Map<String, String> fields) {
        return fields.entrySet().stream()
                .filter(entry -> !HASH_FIELD.equals(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + KEY_VALUE_SEPARATOR + entry.getValue())
                .collect(Collectors.joining(LINE_SEPARATOR));
    }

    private String hmacHex(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKey, HMAC_ALGORITHM));
            return java.util.HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 not available", e);
        }
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance(DIGEST_ALGORITHM).digest(input);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static Long parseLong(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
