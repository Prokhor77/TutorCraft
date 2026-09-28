package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.courses.ItemType;
import com.tutorcraft.core.courses.domain.CourseItem.KeyDates;
import com.tutorcraft.core.courses.domain.StructuredValues;
import com.tutorcraft.core.courses.spi.ActivityType;
import com.tutorcraft.core.shared.domain.ValidationException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Реестр типов элементов (FR-EXT-01, DATA-04): собирает все бины {@link ActivityType}.
 * Тип без реализации (например, модуль-владелец не подключён) недоступен для создания — ошибка валидации.
 */
@Component
public class ActivityTypeRegistry {

    static final String TYPE_FIELD = "type";
    static final String INVALID_TYPE_CODE = "invalid_item_type";
    private static final String KIND = "kind";

    private final Map<ItemType, ActivityType> types = new EnumMap<>(ItemType.class);

    public ActivityTypeRegistry(List<ActivityType> activityTypes) {
        activityTypes.forEach(type -> {
            if (types.putIfAbsent(type.type(), type) != null) {
                throw new IllegalStateException("Duplicate ActivityType for " + type.type().key());
            }
        });
    }

    public ActivityType require(ItemType type) {
        ActivityType activityType = types.get(type);
        if (activityType == null) {
            throw ValidationException.single(TYPE_FIELD, INVALID_TYPE_CODE, "Item type is not available");
        }
        return activityType;
    }

    /** Настройки нового элемента: умолчания типа ⊕ переданные (UX-02). */
    public Map<String, Object> initialSettings(ItemType type, Map<String, Object> requested) {
        ActivityType activityType = require(type);
        return normalize(activityType, StructuredValues.merge(activityType.defaults(), requested));
    }

    /** Правка настроек: текущие ⊕ patch, затем проверка схемой типа. */
    public Map<String, Object> mergedSettings(ItemType type, Map<String, Object> current, Map<String, Object> patch) {
        return normalize(require(type), StructuredValues.merge(current, patch));
    }

    public KeyDates keyDates(ItemType type, Map<String, Object> settings) {
        ActivityType activityType = require(type);
        return new KeyDates(activityType.dueAt(settings).orElse(null), activityType.openAt(settings).orElse(null),
                activityType.closeAt(settings).orElse(null));
    }

    /** Настройки для студента; тип без реализации — только kind (ничего лишнего не раскрываем). */
    public Map<String, Object> learnerView(ItemType type, Map<String, Object> settings) {
        ActivityType activityType = types.get(type);
        return activityType == null ? Map.of(KIND, type.key()) : activityType.learnerView(settings);
    }

    public Set<UUID> referencedFileIds(ItemType type, Map<String, Object> settings) {
        ActivityType activityType = types.get(type);
        return activityType == null ? Set.of() : activityType.referencedFileIds(settings);
    }

    private static Map<String, Object> normalize(ActivityType activityType, Map<String, Object> merged) {
        merged.put(KIND, activityType.type().key());
        return activityType.validate(merged);
    }
}
