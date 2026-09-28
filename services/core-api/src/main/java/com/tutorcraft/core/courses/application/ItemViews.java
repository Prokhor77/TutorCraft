package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.courses.Availability;
import com.tutorcraft.core.courses.ItemType;
import com.tutorcraft.core.courses.application.OutlineView.OutlineItemView;
import com.tutorcraft.core.courses.domain.CourseItem;
import com.tutorcraft.core.files.FilesApi.VideoInfo;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Отображение элементов в Item/ItemDetail/OutlineItem (контракт §5). */
@Component
class ItemViews {

    private static final String FILE_ID = "fileId";
    private static final String VIDEO_STATUS = "videoStatus";
    private static final String HLS_URL = "hlsUrl";

    private final ActivityTypeRegistry types;
    private final CourseContentFiles files;

    ItemViews(ActivityTypeRegistry types, CourseContentFiles files) {
        this.types = types;
        this.files = files;
    }

    /** Полное представление для персонала (все настройки). permissions == null → Item без поля permissions. */
    ItemView staff(CourseItem item, List<String> permissions) {
        return build(item, item.settings(), Availability.open(), null, null, permissions);
    }

    /** Представление для учащегося: настройки через ActivityType.learnerView. */
    ItemView learner(CourseItem item, Availability availability, String completion, String status, List<String> permissions) {
        return build(item, types.learnerView(item.type(), item.settings()), availability, completion, status, permissions);
    }

    OutlineItemView outline(CourseItem item, Availability availability, String completion, String status) {
        return new OutlineItemView(item.id(), item.type().key(), item.title(), item.position(), item.visibility().key(),
                item.publishAt(), item.dates().dueAt(), availability, completion, status, item.version());
    }

    private ItemView build(CourseItem item, Map<String, Object> settings, Availability availability, String completion,
                           String status, List<String> permissions) {
        return new ItemView(item.id(), item.type().key(), item.title(), item.position(), item.visibility().key(),
                item.publishAt(), item.dates().dueAt(), availability, completion, status, item.version(), item.moduleId(),
                item.courseId(), withVideo(item, settings), item.content(), item.completionRule(), item.conditions(),
                permissions);
    }

    /** Видео: статус транскодирования и HLS-адрес (ADR-008). */
    private Map<String, Object> withVideo(CourseItem item, Map<String, Object> settings) {
        if (item.type() != ItemType.VIDEO || settings == null || !(settings.get(FILE_ID) instanceof String raw)) {
            return settings;
        }
        Optional<VideoInfo> video = parse(raw).flatMap(fileId -> files.video(item.tenantId(), fileId));
        Map<String, Object> result = new LinkedHashMap<>(settings);
        result.put(VIDEO_STATUS, video.map(VideoInfo::status).orElse(null));
        result.put(HLS_URL, video.map(VideoInfo::hlsUrl).orElse(null));
        return result;
    }

    private static Optional<UUID> parse(String raw) {
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
