package com.tutorcraft.core.courses.application.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tutorcraft.core.shared.domain.FieldViolation;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.security.CurrentUser;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** FR-CONTENT-03, DATA-04: схемы настроек материалов (страница, файл, ссылка, папка, видео). */
class MaterialActivityTypesTest {

    private static final UUID FILE = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();

    private final VideoActivityType video = new VideoActivityType(tenantId -> Set.of("www.youtube.com"),
            () -> Optional.of(new CurrentUser(UUID.randomUUID(), TENANT, Set.of())));

    private static Map<String, Object> settings(Object... keyValues) {
        Map<String, Object> result = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put((String) keyValues[i], keyValues[i + 1]);
        }
        return result;
    }

    private static String firstCode(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected ValidationException");
        } catch (ValidationException e) {
            return e.violations().stream().map(FieldViolation::code).findFirst().orElseThrow();
        }
    }

    @Test
    void pageIgnoresUnknownSettings() {
        assertThat(new PageActivityType().validate(settings("kind", "page", "script", "x"))).containsOnlyKeys("kind");
    }

    @Test
    void fileAcceptsEmptyAndValidIdOnly() {
        FileActivityType file = new FileActivityType();

        assertThat(file.defaults()).containsEntry("fileId", null);
        assertThat(file.validate(settings("fileId", FILE.toString()))).containsEntry("fileId", FILE.toString());
        assertThat(file.referencedFileIds(file.validate(settings("fileId", FILE.toString())))).containsExactly(FILE);
        assertThat(firstCode(() -> file.validate(settings("fileId", "abc")))).isEqualTo("invalid_uuid");
    }

    @Test
    void urlAllowsOnlyHttpAndHttps() {
        UrlActivityType url = new UrlActivityType();

        assertThat(url.validate(settings("url", " https://example.com/a "))).containsEntry("url", "https://example.com/a");
        assertThat(url.validate(settings())).containsEntry("url", "");
        assertThat(firstCode(() -> url.validate(settings("url", "javascript:alert(1)")))).isEqualTo("invalid_url");
        assertThat(firstCode(() -> url.validate(settings("url", "ftp://example.com")))).isEqualTo("invalid_url");
    }

    @Test
    void folderDeduplicatesAndLimitsFiles() {
        FolderActivityType folder = new FolderActivityType();

        Map<String, Object> normalized = folder.validate(settings("fileIds", List.of(FILE.toString(), FILE.toString())));

        assertThat(normalized.get("fileIds")).isEqualTo(List.of(FILE.toString()));
        assertThat(folder.referencedFileIds(normalized)).containsExactly(FILE);
        List<String> tooMany = IntStream.rangeClosed(0, FolderActivityType.MAX_FILES)
                .mapToObj(i -> UUID.randomUUID().toString()).toList();
        assertThat(firstCode(() -> folder.validate(settings("fileIds", tooMany)))).isEqualTo("invalid");
        assertThat(firstCode(() -> folder.validate(settings("fileIds", List.of("x"))))).isEqualTo("invalid_uuid");
    }

    @Test
    void videoAcceptsUploadedFileOrWhitelistedEmbed() {
        assertThat(video.validate(settings("fileId", FILE.toString()))).containsEntry("fileId", FILE.toString())
                .containsEntry("embedUrl", null);
        assertThat(video.validate(settings("embedUrl", "https://www.youtube.com/embed/abc")))
                .containsEntry("embedUrl", "https://www.youtube.com/embed/abc");
    }

    @Test
    void videoDropsComputedFieldsOnWrite() {
        Map<String, Object> normalized = video.validate(settings("fileId", FILE.toString(), "videoStatus", "ready",
                "hlsUrl", "https://cdn/x.m3u8"));

        assertThat(normalized).containsOnlyKeys("kind", "fileId", "embedUrl");
    }

    @Test
    void videoRejectsForeignEmbedAndBothSources() {
        assertThat(firstCode(() -> video.validate(settings("embedUrl", "https://evil.example.com/v"))))
                .isEqualTo("embed_not_allowed");
        assertThatThrownBy(() -> video.validate(settings("fileId", FILE.toString(), "embedUrl",
                "https://www.youtube.com/embed/abc"))).isInstanceOf(ValidationException.class);
    }

    @Test
    void videoWithoutUserContextAllowsNoEmbeds() {
        VideoActivityType anonymous = new VideoActivityType(tenantId -> Set.of("www.youtube.com"), Optional::empty);
        assertThat(firstCode(() -> anonymous.validate(settings("embedUrl", "https://www.youtube.com/embed/abc"))))
                .isEqualTo("embed_not_allowed");
    }
}
