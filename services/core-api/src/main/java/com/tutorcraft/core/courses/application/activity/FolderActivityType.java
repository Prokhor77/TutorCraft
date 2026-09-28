package com.tutorcraft.core.courses.application.activity;

import com.tutorcraft.core.courses.ItemType;
import com.tutorcraft.core.courses.spi.ActivityType;
import com.tutorcraft.core.shared.domain.Validator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Папка файлов (FR-CONTENT-03): упорядоченный список загруженных файлов. */
@Component
class FolderActivityType implements ActivityType {

    static final int MAX_FILES = 100;
    private static final String FILE_IDS = "fileIds";

    @Override
    public ItemType type() {
        return ItemType.FOLDER;
    }

    @Override
    public Map<String, Object> defaults() {
        return settings(List.of());
    }

    @Override
    public Map<String, Object> validate(Map<String, Object> settings) {
        Validator validator = new Validator();
        List<String> fileIds = SettingsFields.uuidList(settings, FILE_IDS, MAX_FILES, validator);
        validator.throwIfInvalid();
        return settings(fileIds);
    }

    @Override
    public Set<UUID> referencedFileIds(Map<String, Object> settings) {
        return SettingsFields.toUuids(settings.get(FILE_IDS));
    }

    private static Map<String, Object> settings(List<String> fileIds) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(SettingsFields.KIND, ItemType.FOLDER.key());
        result.put(FILE_IDS, List.copyOf(fileIds));
        return result;
    }
}
