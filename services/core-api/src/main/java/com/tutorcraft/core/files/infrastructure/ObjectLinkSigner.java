package com.tutorcraft.core.files.infrastructure;

import com.tutorcraft.core.shared.config.AppProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * HMAC-SHA256-подпись ссылок локального хранилища. Ключ выводится из JWT_SECRET с отдельным контекстом, поэтому
 * отдельный секрет в .env не нужен, а подпись ссылки нельзя использовать как JWT и наоборот. Части сообщения
 * URL-кодируются перед склейкой: разделитель не может встретиться внутри значения.
 */
@Component
@LocalStorageEnabled
class ObjectLinkSigner {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String KEY_CONTEXT = "tutorcraft/storage-links/v1";
    private static final String SEPARATOR = "\n";

    private final SecretKeySpec key;

    ObjectLinkSigner(AppProperties properties) {
        byte[] master = properties.security().jwtSecret().getBytes(StandardCharsets.UTF_8);
        this.key = new SecretKeySpec(hmac(new SecretKeySpec(master, ALGORITHM), KEY_CONTEXT), ALGORITHM);
    }

    String sign(Object... parts) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(key, message(parts)));
    }

    /** Сравнение за постоянное время; пустая подпись всегда недействительна. */
    boolean verify(String signature, Object... parts) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        byte[] expected = sign(parts).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.US_ASCII));
    }

    private static String message(Object... parts) {
        return Stream.of(parts)
                .map(part -> URLEncoder.encode(String.valueOf(part), StandardCharsets.UTF_8))
                .collect(Collectors.joining(SEPARATOR));
    }

    private static byte[] hmac(SecretKeySpec secret, String message) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(secret);
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }
}
