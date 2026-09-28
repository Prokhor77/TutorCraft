package com.tutorcraft.core.billing.domain;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Проверка заголовка Stripe-Signature: {@code t=<unix>,v1=<hex>[,v1=...]}; подпись — HMAC-SHA256 секрета вебхука
 * над {@code t + "." + payload}. Метка времени не старше допуска (защита от повтора), сравнение за постоянное время.
 */
public final class StripeSignature {

    public static final String HEADER = "Stripe-Signature";
    public static final Duration DEFAULT_TOLERANCE = Duration.ofMinutes(5);
    private static final String ALGORITHM = "HmacSHA256";
    private static final String TIMESTAMP_KEY = "t";
    private static final String SIGNATURE_KEY = "v1";
    private static final String PAIR_SEPARATOR = ",";
    private static final String KEY_VALUE_SEPARATOR = "=";
    private static final byte PAYLOAD_SEPARATOR = '.';

    private StripeSignature() {
    }

    public static boolean verify(String header, byte[] payload, String secret, Instant now, Duration tolerance) {
        if (header == null || payload == null || secret == null || secret.isBlank()) {
            return false;
        }
        ParsedHeader parsed = parse(header);
        if (parsed.timestamp() == null || parsed.signatures().isEmpty()) {
            return false;
        }
        long age = Math.abs(now.getEpochSecond() - parsed.timestamp());
        if (age > tolerance.toSeconds()) {
            return false;
        }
        byte[] expected = hmac(secret, signedPayload(parsed.timestamp(), payload));
        return parsed.signatures().stream().anyMatch(candidate -> MessageDigest.isEqual(expected, candidate));
    }

    /** Подпись для тестов и локальной отладки: {@code t=<unix>,v1=<hex>}. */
    public static String sign(String secret, long unixSeconds, byte[] payload) {
        return TIMESTAMP_KEY + KEY_VALUE_SEPARATOR + unixSeconds + PAIR_SEPARATOR + SIGNATURE_KEY + KEY_VALUE_SEPARATOR
                + HexFormat.of().formatHex(hmac(secret, signedPayload(unixSeconds, payload)));
    }

    private static ParsedHeader parse(String header) {
        Long timestamp = null;
        List<byte[]> signatures = new ArrayList<>();
        for (String pair : header.split(PAIR_SEPARATOR)) {
            String[] parts = pair.trim().split(KEY_VALUE_SEPARATOR, 2);
            if (parts.length != 2) {
                continue;
            }
            if (TIMESTAMP_KEY.equals(parts[0])) {
                timestamp = parseLong(parts[1]);
            } else if (SIGNATURE_KEY.equals(parts[0])) {
                parseHex(parts[1]).ifPresent(signatures::add);
            }
        }
        return new ParsedHeader(timestamp, signatures);
    }

    private static Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Некорректный hex не может совпасть с подписью — такая часть заголовка не учитывается. */
    private static Optional<byte[]> parseHex(String value) {
        try {
            return Optional.of(HexFormat.of().parseHex(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static byte[] signedPayload(long timestamp, byte[] payload) {
        byte[] prefix = Long.toString(timestamp).getBytes(StandardCharsets.UTF_8);
        byte[] signed = new byte[prefix.length + 1 + payload.length];
        System.arraycopy(prefix, 0, signed, 0, prefix.length);
        signed[prefix.length] = PAYLOAD_SEPARATOR;
        System.arraycopy(payload, 0, signed, prefix.length + 1, payload.length);
        return signed;
    }

    private static byte[] hmac(String secret, byte[] data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 not available", e);
        }
    }

    private record ParsedHeader(Long timestamp, List<byte[]> signatures) {
    }
}
