package com.tutorcraft.core.communication.forum.application;

import com.tutorcraft.core.communication.forum.domain.ForumSettings;
import com.tutorcraft.core.courses.ItemType;
import com.tutorcraft.core.courses.spi.ActivityType;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Тип элемента «Форум» (FR-EXT-01, DATA-04). Умолчания: обычный форум, окно правки 30 минут. */
@Component
class ForumActivityType implements ActivityType {

    @Override
    public ItemType type() {
        return ItemType.FORUM;
    }

    @Override
    public Map<String, Object> defaults() {
        return ForumSettings.defaults().toMap();
    }

    @Override
    public Map<String, Object> validate(Map<String, Object> settings) {
        return ForumSettings.parse(settings).toMap();
    }
}
