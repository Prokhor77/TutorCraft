package com.tutorcraft.core.files.application;

import com.tutorcraft.core.files.application.FileMetaView.VideoView;
import com.tutorcraft.core.files.domain.StoredFile;
import org.springframework.stereotype.Component;

/** Сборка FileMeta для вызывающего, у которого уже проверено право чтения. */
@Component
public class FileViews {

    private final FileRepository files;
    private final FileUrls urls;

    public FileViews(FileRepository files, FileUrls urls) {
        this.files = files;
        this.urls = urls;
    }

    public FileMetaView view(StoredFile file) {
        String url = file.isReady() ? urls.download(file) : null;
        VideoView video = file.isVideo() ? videoOf(file) : null;
        return new FileMetaView(file.id(), file.name(), file.sizeBytes(), file.mime(), file.status().key(), url, video);
    }

    private VideoView videoOf(StoredFile file) {
        return files.findVideo(file.tenantId(), file.id())
                .map(video -> new VideoView(video.status(), urls.hls(video.masterPlaylistKey()), video.durationSec()))
                .orElse(null);
    }
}
