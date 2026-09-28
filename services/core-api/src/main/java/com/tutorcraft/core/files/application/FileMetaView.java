package com.tutorcraft.core.files.application;

import java.util.UUID;

/** Контракт FileMeta. video — только для файлов назначения video. */
public record FileMetaView(UUID id, String name, long size, String mime, String status, String url, VideoView video) {

    public record VideoView(String status, String hlsUrl, Integer durationSec) {
    }
}
