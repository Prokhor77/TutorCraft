package com.tutorcraft.core.integrations.domain;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Подпись тела вебхука (контракт §14): {@code X-TC-Signature: t=<unix>,v1=<hex(hmac_sha256(secret, t + "." + body))>}.
 * Метка времени в подписи защищает получателя от повторного воспроизведения.
 */
public final class WebhookSignature {

    public static final String HEADER = "X-TC-Signature";
    private static final String ALGORITHM = "HmacSHA256";
    private static final String TIMESTAMP_PREFIX = "t=";
    private static final String VERSION_PREFIX = ",v1=";
    private static final String PAYLOAD_SEPARATOR = ".";

    private WebhookSignature() {
    }

    public static String header(String secret, long unixSeconds, String body) {
        return TIMESTAMP_PREFIX + unixSeconds + VERSION_PREFIX + hmacHex(secret, unixSeconds + PAYLOAD_SEPARATOR + body);
    }

    static String hmacHex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 not available", e);
        }
    }
}
