package com.tutorcraft.core.communication.notifications.domain;

import com.tutorcraft.core.communication.NotificationCategory;
import java.util.EnumSet;
import java.util.Set;

/**
 * Настройки по умолчанию (FR-NOTIF-02 + гибрид): web — для всех категорий; email — дедлайны, оценки, объявления,
 * аккаунт, продажи; Telegram — дедлайны, оценки, объявления, продажи, готовность видео, новые элементы.
 * Сдачи работ (submission_received) по умолчанию только в web — чтобы не засыпать преподавателя письмами.
 */
public final class PreferenceDefaults {

    private static final Set<NotificationCategory> EMAIL = EnumSet.of(NotificationCategory.DEADLINE,
            NotificationCategory.GRADE_PUBLISHED, NotificationCategory.ANNOUNCEMENT, NotificationCategory.ACCOUNT,
            NotificationCategory.SALE);
    private static final Set<NotificationCategory> TELEGRAM = EnumSet.of(NotificationCategory.DEADLINE,
            NotificationCategory.GRADE_PUBLISHED, NotificationCategory.ANNOUNCEMENT, NotificationCategory.SALE,
            NotificationCategory.VIDEO_READY, NotificationCategory.NEW_ITEM);

    private PreferenceDefaults() {
    }

    public static boolean enabled(NotificationCategory category, NotificationChannel channel) {
        return switch (channel) {
            case WEB -> true;
            case EMAIL -> EMAIL.contains(category);
            case TELEGRAM -> TELEGRAM.contains(category);
        };
    }

    /** Категории, настраиваемые пользователем (служебные письма аккаунта отключить нельзя). */
    public static boolean configurable(NotificationCategory category) {
        return category != NotificationCategory.ACCOUNT;
    }
}
