package com.tutorcraft.core.courses.application.activity;

import com.tutorcraft.core.courses.ItemType;
import com.tutorcraft.core.courses.spi.ActivityType;
import com.tutorcraft.core.shared.domain.Validator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Файл (FR-CONTENT-03): один загруженный файл; до выбора файла fileId = null. */
@Component
class FileActivityType implements ActivityType {

    @Override
    public ItemType type() {
        return ItemType.FILE;
    }

    @Override
    public Map<String, Object> defaults() {
        return settings(null);
    }

    @Override
    public Map<String, Object> validate(Map<String, Object> settings) {
        Validator validator = new Validator();
        String fileId = SettingsFields.optionalUuid(settings, SettingsFields.FILE_ID, validator);
        validator.throwIfInvalid();
        return settings(fileId);
    }

    @Override
    public Set<UUID> referencedFileIds(Map<String, Object> settings) {
        return SettingsFields.toUuids(settings.get(SettingsFields.FILE_ID));
    }

    private static Map<String, Object> settings(String fileId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(SettingsFields.KIND, ItemType.FILE.key());
        result.put(SettingsFields.FILE_ID, fileId);
        return result;
    }
}
