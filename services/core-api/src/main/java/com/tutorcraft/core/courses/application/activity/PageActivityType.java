package com.tutorcraft.core.courses.application.activity;

import com.tutorcraft.core.courses.ItemType;
import com.tutorcraft.core.courses.spi.ActivityType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Страница (FR-CONTENT-03): текст — блочный документ в item.content; настроек нет. */
@Component
class PageActivityType implements ActivityType {

    @Override
    public ItemType type() {
        return ItemType.PAGE;
    }

    @Override
    public Map<String, Object> defaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put(SettingsFields.KIND, ItemType.PAGE.key());
        return defaults;
    }

    @Override
    public Map<String, Object> validate(Map<String, Object> settings) {
        return defaults();
    }
}
