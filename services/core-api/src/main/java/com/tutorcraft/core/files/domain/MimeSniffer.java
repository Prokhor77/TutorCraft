package com.tutorcraft.core.files.domain;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * Определение реального типа файла по сигнатуре (магическим байтам) первых {@link #HEAD_BYTES} байт (NFR-SEC-05).
 * Текст распознаётся эвристически: корректный UTF-8 без управляющих символов, кроме пробельных.
 */
public final class MimeSniffer {

    public static final int HEAD_BYTES = 64;

    private static final byte[] PDF = ascii("%PDF-");
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] GIF87 = ascii("GIF87a");
    private static final byte[] GIF89 = ascii("GIF89a");
    private static final byte[] RIFF = ascii("RIFF");
    private static final byte[] WEBP = ascii("WEBP");
    private static final byte[] WAVE = ascii("WAVE");
    private static final byte[] ZIP = {'P', 'K', 0x03, 0x04};
    private static final byte[] FTYP = ascii("ftyp");
    private static final byte[] QUICKTIME_BRAND = ascii("qt  ");
    private static final byte[] M4A_BRAND = ascii("M4A ");
    private static final byte[] EBML = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};
    private static final byte[] ID3 = ascii("ID3");
    private static final byte[] OGG = ascii("OggS");
    private static final int RIFF_FORMAT_OFFSET = 8;
    private static final int FTYP_OFFSET = 4;
    private static final int BRAND_OFFSET = 8;
    private static final int MPEG_SYNC_MASK = 0xE0;
    private static final int BYTE_MASK = 0xFF;
    private static final int FIRST_PRINTABLE = 0x20;
    private static final int DELETE = 0x7F;
    private static final int ASCII_LIMIT = 0x80;
    private static final int TWO_BYTE_MIN = 0xC2;
    private static final int TWO_BYTE_MAX = 0xDF;
    private static final int THREE_BYTE_MIN = 0xE0;
    private static final int THREE_BYTE_MAX = 0xEF;
    private static final int FOUR_BYTE_MIN = 0xF0;
    private static final int FOUR_BYTE_MAX = 0xF4;
    private static final int CONTINUATION_MASK = 0xC0;
    private static final int CONTINUATION_BITS = 0x80;
    private static final int TWO = 2;
    private static final int THREE = 3;
    private static final int FOUR = 4;

    private MimeSniffer() {
    }

    public static Optional<String> detect(byte[] head) {
        if (head == null || head.length == 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(binaryType(head)).or(() -> isText(head) ? Optional.of(MimeTypes.TEXT) : Optional.empty());
    }

    private static String binaryType(byte[] head) {
        if (startsWith(head, 0, PDF)) {
            return MimeTypes.PDF;
        }
        if (startsWith(head, 0, PNG)) {
            return MimeTypes.PNG;
        }
        if (startsWith(head, 0, JPEG)) {
            return MimeTypes.JPEG;
        }
        if (startsWith(head, 0, GIF87) || startsWith(head, 0, GIF89)) {
            return MimeTypes.GIF;
        }
        if (startsWith(head, 0, ZIP)) {
            return MimeTypes.ZIP;
        }
        if (startsWith(head, 0, RIFF)) {
            return riffType(head);
        }
        if (startsWith(head, FTYP_OFFSET, FTYP)) {
            return isoMediaType(head);
        }
        return streamType(head);
    }

    private static String riffType(byte[] head) {
        if (startsWith(head, RIFF_FORMAT_OFFSET, WEBP)) {
            return MimeTypes.WEBP;
        }
        return startsWith(head, RIFF_FORMAT_OFFSET, WAVE) ? MimeTypes.WAV : null;
    }

    private static String isoMediaType(byte[] head) {
        if (startsWith(head, BRAND_OFFSET, QUICKTIME_BRAND)) {
            return MimeTypes.QUICKTIME;
        }
        return startsWith(head, BRAND_OFFSET, M4A_BRAND) ? MimeTypes.M4A : MimeTypes.MP4;
    }

    private static String streamType(byte[] head) {
        if (startsWith(head, 0, EBML)) {
            return MimeTypes.WEBM;
        }
        if (startsWith(head, 0, OGG)) {
            return MimeTypes.OGG;
        }
        if (startsWith(head, 0, ID3) || isMpegFrameSync(head)) {
            return MimeTypes.MP3;
        }
        return null;
    }

    private static boolean isMpegFrameSync(byte[] head) {
        return head.length > 1 && (head[0] & BYTE_MASK) == BYTE_MASK && (head[1] & MPEG_SYNC_MASK) == MPEG_SYNC_MASK;
    }

    /** Корректный UTF-8 (последняя последовательность может быть обрезана) без управляющих символов, кроме \t \n \r \f. */
    static boolean isText(byte[] head) {
        int index = 0;
        while (index < head.length) {
            int lead = head[index] & BYTE_MASK;
            if (lead < FIRST_PRINTABLE || lead == DELETE) {
                if (!isWhitespaceControl(lead)) {
                    return false;
                }
                index++;
                continue;
            }
            int length = sequenceLength(lead);
            if (length == 0 || !continuationBytesValid(head, index, length)) {
                return false;
            }
            index += length;
        }
        return true;
    }

    private static boolean isWhitespaceControl(int value) {
        return value == '\t' || value == '\n' || value == '\r' || value == '\f';
    }

    /** Длина UTF-8-последовательности по ведущему байту; 0 — недопустимый ведущий байт. */
    private static int sequenceLength(int lead) {
        if (lead < ASCII_LIMIT) {
            return 1;
        }
        if (lead >= TWO_BYTE_MIN && lead <= TWO_BYTE_MAX) {
            return TWO;
        }
        if (lead >= THREE_BYTE_MIN && lead <= THREE_BYTE_MAX) {
            return THREE;
        }
        return lead >= FOUR_BYTE_MIN && lead <= FOUR_BYTE_MAX ? FOUR : 0;
    }

    private static boolean continuationBytesValid(byte[] head, int start, int length) {
        int end = Math.min(start + length, head.length);
        for (int i = start + 1; i < end; i++) {
            if ((head[i] & CONTINUATION_MASK) != CONTINUATION_BITS) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        if (data.length < offset + prefix.length) {
            return false;
        }
        return Arrays.equals(data, offset, offset + prefix.length, prefix, 0, prefix.length);
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
