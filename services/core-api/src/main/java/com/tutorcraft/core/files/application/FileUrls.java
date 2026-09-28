package com.tutorcraft.core.files.application;

import com.tutorcraft.core.files.FilesApi.VideoInfo;
import com.tutorcraft.core.files.application.FileRepository.VideoRecord;
import com.tutorcraft.core.files.domain.MimeTypes;
import com.tutorcraft.core.files.domain.StoredFile;
import com.tutorcraft.core.shared.config.AppProperties;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** URL для скачивания (pre-signed GET) и воспроизведения HLS. Зависит только от хранилища. */
@Component
public class FileUrls {

    private final ObjectStorage storage;
    private final Duration downloadTtl;

    public FileUrls(ObjectStorage storage, AppProperties properties) {
        this.storage = storage;
        this.downloadTtl = properties.storage().downloadUrlTtl();
    }

    /** Небезопасные для браузера типы отдаются с Content-Disposition: attachment (NFR-SEC-05). */
    public String download(StoredFile file) {
        return storage.presignDownload(file.storageKey(), file.name(), file.mime(), MimeTypes.isUnsafe(file.mime()), downloadTtl)
                .url();
    }

    /** HLS-мастер-плейлист раздаётся с публичного префикса hls/ (MVP, ADR-008). */
    public String hls(String masterPlaylistKey) {
        return masterPlaylistKey == null ? null : storage.publicUrl(masterPlaylistKey);
    }

    public VideoInfo video(VideoRecord video) {
        return new VideoInfo(video.status(), hls(video.masterPlaylistKey()), video.durationSec());
    }
}
