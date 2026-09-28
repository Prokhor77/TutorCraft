package com.tutorcraft.core.files.domain;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Потоковый SHA-256 содержимого (дедупликация файлов). */
public final class Sha256 {

    private static final int BUFFER_BYTES = 64 * 1024;

    private Sha256() {
    }

    public static String hex(InputStream input) throws IOException {
        MessageDigest digest = newDigest();
        byte[] buffer = new byte[BUFFER_BYTES];
        int read;
        while ((read = input.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
