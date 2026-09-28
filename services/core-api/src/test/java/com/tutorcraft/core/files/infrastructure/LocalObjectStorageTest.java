package com.tutorcraft.core.files.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tutorcraft.core.files.application.DirectObjectTransfer.DownloadGrant;
import com.tutorcraft.core.files.application.DirectObjectTransfer.StoredObject;
import com.tutorcraft.core.files.application.DirectObjectTransfer.UploadGrant;
import com.tutorcraft.core.files.application.FilesErrors;
import com.tutorcraft.core.shared.config.AppProperties;
import com.tutorcraft.core.shared.domain.DomainException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Локальное хранилище: подписанные ссылки, приём и выдача объектов (STORAGE_DRIVER=local). */
class LocalObjectStorageTest {

    private static final Instant NOW = Instant.parse("2026-09-29T10:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(15);
    private static final String KEY = "t/tenant/2026/09/file-1";
    private static final String PNG = "image/png";
    private static final byte[] BODY = "png-bytes".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path root;

    private LocalFileStore files;
    private ObjectLinkSigner signer;
    private LocalObjectStorage storage;
    private LocalObjectTransfer transfer;

    @BeforeEach
    void setUp() {
        AppProperties properties = properties(root);
        files = new LocalFileStore(properties);
        signer = new ObjectLinkSigner(properties);
        storage = new LocalObjectStorage(files, signer, clockAt(NOW));
        transfer = new LocalObjectTransfer(files, signer, clockAt(NOW));
    }

    @Test
    void uploadedObjectIsStoredUnderItsKey() throws IOException {
        Map<String, String> link = query(storage.presignUpload(KEY, PNG, BODY.length, TTL).url());

        transfer.receive(KEY, uploadGrant(link, PNG), BODY.length, body());

        assertThat(Files.readAllBytes(root.resolve(KEY))).isEqualTo(BODY);
        assertThat(storage.sizeOf(KEY)).contains((long) BODY.length);
        assertThat(storage.readPrefix(KEY, 3)).isEqualTo("png".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void uploadIsRejectedWhenContentTypeDiffersFromSignedOne() {
        Map<String, String> link = query(storage.presignUpload(KEY, PNG, BODY.length, TTL).url());

        assertCode(() -> transfer.receive(KEY, uploadGrant(link, "text/html"), BODY.length, body()), FilesErrors.LINK_INVALID);
        assertThat(storage.sizeOf(KEY)).isEmpty();
    }

    @Test
    void uploadIsRejectedAfterExpiry() {
        Map<String, String> link = query(storage.presignUpload(KEY, PNG, BODY.length, TTL).url());
        LocalObjectTransfer later = new LocalObjectTransfer(files, signer, clockAt(NOW.plus(TTL).plusSeconds(1)));

        assertCode(() -> later.receive(KEY, uploadGrant(link, PNG), BODY.length, body()), FilesErrors.LINK_EXPIRED);
    }

    @Test
    void bodyLongerThanSignedSizeIsNotStored() {
        Map<String, String> link = query(storage.presignUpload(KEY, PNG, BODY.length - 1, TTL).url());

        assertCode(() -> transfer.receive(KEY, uploadGrant(link, PNG), -1, body()), FilesErrors.SIZE_MISMATCH);
        assertThat(storage.sizeOf(KEY)).isEmpty();
        assertThat(root.resolve(KEY).getParent()).isEmptyDirectory();
    }

    @Test
    void objectCannotBeOverwritten() {
        Map<String, String> link = query(storage.presignUpload(KEY, PNG, BODY.length, TTL).url());
        transfer.receive(KEY, uploadGrant(link, PNG), BODY.length, body());

        assertCode(() -> transfer.receive(KEY, uploadGrant(link, PNG), BODY.length, body()), FilesErrors.ALREADY_UPLOADED);
    }

    @Test
    void signedDownloadReturnsObjectWithDisposition() {
        upload();
        Map<String, String> link = query(storage.presignDownload(KEY, "отчёт 1.png", PNG, true, TTL).url());

        StoredObject object = transfer.read(KEY, downloadGrant(link));

        assertThat(object.contentType()).isEqualTo(PNG);
        assertThat(object.contentDisposition()).startsWith("attachment");
        assertThat(object.immutable()).isFalse();
    }

    @Test
    void tamperedDownloadLinkIsRejected() {
        upload();
        Map<String, String> link = query(storage.presignDownload(KEY, "a.png", PNG, true, TTL).url());
        link.put("dl", "0");

        assertCode(() -> transfer.read(KEY, downloadGrant(link)), FilesErrors.LINK_INVALID);
    }

    @Test
    void onlyHlsObjectsArePublic() throws IOException {
        upload();
        Path playlist = root.resolve("hls/file-1/master.m3u8");
        Files.createDirectories(playlist.getParent());
        Files.writeString(playlist, "#EXTM3U");
        DownloadGrant none = new DownloadGrant(null, null, false, null, null);

        StoredObject object = transfer.read("hls/file-1/master.m3u8", none);

        assertThat(storage.publicUrl("hls/file-1/master.m3u8")).isEqualTo("/storage/hls/file-1/master.m3u8");
        assertThat(object.contentType()).isEqualTo("application/vnd.apple.mpegurl");
        assertThat(object.immutable()).isTrue();
        assertCode(() -> transfer.read(KEY, none), FilesErrors.LINK_INVALID);
    }

    @Test
    void keysOutsideTheRootAreRejected() {
        assertThat(LocalFileStore.isValidKey("../etc/passwd")).isFalse();
        assertThat(LocalFileStore.isValidKey("/etc/passwd")).isFalse();
        assertThat(LocalFileStore.isValidKey("hls/../../x")).isFalse();
        assertThat(LocalFileStore.isValidKey(".hidden")).isFalse();
        assertThat(LocalFileStore.isValidKey(KEY)).isTrue();
        assertCode(() -> transfer.read("../secret", new DownloadGrant(null, null, false, null, null)), FilesErrors.NOT_FOUND);
    }

    private void upload() {
        Map<String, String> link = query(storage.presignUpload(KEY, PNG, BODY.length, TTL).url());
        transfer.receive(KEY, uploadGrant(link, PNG), BODY.length, body());
    }

    private static InputStream body() {
        return new ByteArrayInputStream(BODY);
    }

    private static UploadGrant uploadGrant(Map<String, String> link, String contentType) {
        return new UploadGrant(contentType, Long.valueOf(link.get("size")), Long.valueOf(link.get("exp")), link.get("sig"));
    }

    private static DownloadGrant downloadGrant(Map<String, String> link) {
        return new DownloadGrant(link.get("ct"), link.get("name"), "1".equals(link.get("dl")), Long.valueOf(link.get("exp")),
                link.get("sig"));
    }

    /** Разбор query так же, как это делает Spring MVC для @RequestParam. */
    private static Map<String, String> query(String url) {
        assertThat(url).startsWith("/storage/" + KEY + "?");
        Map<String, String> params = new HashMap<>();
        for (String pair : url.substring(url.indexOf('?') + 1).split("&")) {
            int separator = pair.indexOf('=');
            params.put(pair.substring(0, separator), URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8));
        }
        return params;
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    private static Clock clockAt(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private static AppProperties properties(Path root) {
        AppProperties properties = mock(AppProperties.class);
        AppProperties.Storage storage = mock(AppProperties.Storage.class);
        AppProperties.Security security = mock(AppProperties.Security.class);
        when(properties.storage()).thenReturn(storage);
        when(properties.security()).thenReturn(security);
        when(storage.localRoot()).thenReturn(root.toString());
        when(security.jwtSecret()).thenReturn("test-secret-test-secret-test-secret-0123456789");
        return properties;
    }
}
