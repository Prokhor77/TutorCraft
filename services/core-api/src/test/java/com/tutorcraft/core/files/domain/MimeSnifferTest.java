package com.tutorcraft.core.files.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import java.util.stream.Stream;

class MimeSnifferTest {

    static Stream<Arguments> signatures() {
        return Stream.of(
                Arguments.of(ascii("%PDF-1.7\n%âãÏÓ"), MimeTypes.PDF),
                Arguments.of(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0), MimeTypes.PNG),
                Arguments.of(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10), MimeTypes.JPEG),
                Arguments.of(ascii("GIF89a\u0001\u0000"), MimeTypes.GIF),
                Arguments.of(concat(ascii("RIFF"), bytes(0x24, 0, 0, 0), ascii("WEBPVP8 ")), MimeTypes.WEBP),
                Arguments.of(concat(ascii("RIFF"), bytes(0x24, 0, 0, 0), ascii("WAVEfmt ")), MimeTypes.WAV),
                Arguments.of(bytes('P', 'K', 3, 4, 20, 0), MimeTypes.ZIP),
                Arguments.of(concat(bytes(0, 0, 0, 0x18), ascii("ftypmp42")), MimeTypes.MP4),
                Arguments.of(concat(bytes(0, 0, 0, 0x14), ascii("ftypqt  ")), MimeTypes.QUICKTIME),
                Arguments.of(concat(bytes(0, 0, 0, 0x20), ascii("ftypM4A ")), MimeTypes.M4A),
                Arguments.of(bytes(0x1A, 0x45, 0xDF, 0xA3, 0x9F), MimeTypes.WEBM),
                Arguments.of(ascii("ID3\u0004\u0000"), MimeTypes.MP3),
                Arguments.of(bytes(0xFF, 0xFB, 0x90, 0x64), MimeTypes.MP3),
                Arguments.of(ascii("OggS\u0000\u0002"), MimeTypes.OGG),
                Arguments.of("email,firstName,lastName\nanna@school.ru,Анна,Петрова\n".getBytes(StandardCharsets.UTF_8),
                        MimeTypes.TEXT));
    }

    @ParameterizedTest
    @MethodSource("signatures")
    void detectsTypeBySignature(byte[] head, String expected) {
        assertThat(MimeSniffer.detect(head)).contains(expected);
    }

    @Test
    void unknownBinaryIsNotDetected() {
        assertThat(MimeSniffer.detect(bytes(0x00, 0x01, 0x02, 0x03, 0xFE))).isEmpty();
        assertThat(MimeSniffer.detect(new byte[0])).isEmpty();
        assertThat(MimeSniffer.detect(null)).isEmpty();
    }

    @Test
    void textMayEndWithTruncatedMultibyteCharacter() {
        byte[] full = "Привет".getBytes(StandardCharsets.UTF_8);
        byte[] truncated = Arrays.copyOf(full, full.length - 1);

        assertThat(MimeSniffer.detect(truncated)).contains(MimeTypes.TEXT);
    }

    @Test
    void invalidUtf8IsNotText() {
        assertThat(MimeSniffer.isText(bytes('a', 0xC0, 0x80))).isFalse();
        assertThat(MimeSniffer.isText(bytes('a', 0xE2, 'b', 'c'))).isFalse();
    }

    @Test
    void htmlDisguisedAsImageIsNotAnImage() {
        String detected = MimeSniffer.detect(ascii("<html><script>alert(1)</script>")).orElseThrow();

        assertThat(MimeTypes.compatible(MimeTypes.PNG, detected)).isFalse();
    }

    @Test
    void compatibilityCoversContainerFormats() {
        assertThat(MimeTypes.compatible(MimeTypes.DOCX, MimeTypes.ZIP)).isTrue();
        assertThat(MimeTypes.compatible(MimeTypes.CSV, MimeTypes.TEXT)).isTrue();
        assertThat(MimeTypes.compatible(MimeTypes.MP4, MimeTypes.QUICKTIME)).isTrue();
        assertThat(MimeTypes.compatible(MimeTypes.MATROSKA, MimeTypes.WEBM)).isTrue();
        assertThat(MimeTypes.compatible(MimeTypes.PDF, MimeTypes.ZIP)).isFalse();
        assertThat(MimeTypes.compatible(MimeTypes.JPEG, MimeTypes.PNG)).isFalse();
    }

    @Test
    void normalizeStripsParametersAndCase() {
        assertThat(MimeTypes.normalize("Text/CSV; charset=UTF-8")).isEqualTo(MimeTypes.CSV);
    }

    @Test
    void scriptableTypesAreUnsafe() {
        assertThat(MimeTypes.isUnsafe("image/svg+xml")).isTrue();
        assertThat(MimeTypes.isUnsafe("text/html")).isTrue();
        assertThat(MimeTypes.isUnsafe(MimeTypes.PDF)).isFalse();
    }

    @Test
    void purposesLimitSizeAndTypes() {
        long configuredVideoLimit = 2_000_000_000L;

        assertThat(FilePurpose.AVATAR.maxBytes(configuredVideoLimit)).isEqualTo(5L * 1024 * 1024);
        assertThat(FilePurpose.VIDEO.maxBytes(configuredVideoLimit)).isEqualTo(configuredVideoLimit);
        assertThat(FilePurpose.AVATAR.allows(MimeTypes.PDF)).isFalse();
        assertThat(FilePurpose.CONTENT.allows("text/html")).isFalse();
    }

    @Test
    void fileNamesAreSanitized() {
        assertThat(FileNames.sanitize("../../etc/passwd")).isEqualTo("passwd");
        assertThat(FileNames.sanitize("C:\\Users\\me\\отчёт<1>.docx")).isEqualTo("отчёт_1_.docx");
        assertThat(FileNames.sanitize("  ..  ")).isEqualTo("file");
        assertThat(FileNames.sanitize("a".repeat(300) + ".pdf")).hasSize(FileNames.MAX_LENGTH).endsWith(".pdf");
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    private static byte[] concat(byte[]... parts) {
        int length = Arrays.stream(parts).mapToInt(part -> part.length).sum();
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }
}
