package com.tutorcraft.core.communication.notifications.domain;

import com.tutorcraft.core.communication.NotificationCategory;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Выбор каналов доставки для получателя (FR-NOTIF-02). Правила по порядку:
 * <ol>
 *   <li>письмо на указанный адрес (приглашение без аккаунта) — только email;</li>
 *   <li>категория account — только email (ссылки со секретами не хранятся в центре уведомлений и не идут в Telegram);</li>
 *   <li>неактивный получатель (приостановлен, не принял приглашение) — ничего;</li>
 *   <li>доступные каналы: web; email — если адрес доставляемый; telegram — если чат привязан;</li>
 *   <li>force — все доступные, иначе — по настройке пользователя, а без неё — по умолчанию категории.</li>
 * </ol>
 */
public final class ChannelResolver {

    private ChannelResolver() {
    }

    public record Recipient(boolean active, boolean emailDeliverable, boolean telegramLinked) {
    }

    public static Set<NotificationChannel> resolve(NotificationCategory category, Recipient recipient,
                                                   Map<NotificationChannel, Boolean> preferences, boolean force,
                                                   boolean directEmail) {
        if (directEmail) {
            return EnumSet.of(NotificationChannel.EMAIL);
        }
        if (category == NotificationCategory.ACCOUNT) {
            return recipient.emailDeliverable() ? EnumSet.of(NotificationChannel.EMAIL) : EnumSet.noneOf(NotificationChannel.class);
        }
        if (!recipient.active()) {
            return EnumSet.noneOf(NotificationChannel.class);
        }
        Set<NotificationChannel> channels = available(recipient);
        if (!force) {
            channels.removeIf(channel -> !preferences.getOrDefault(channel, PreferenceDefaults.enabled(category, channel)));
        }
        return channels;
    }

    private static Set<NotificationChannel> available(Recipient recipient) {
        Set<NotificationChannel> channels = EnumSet.of(NotificationChannel.WEB);
        if (recipient.emailDeliverable()) {
            channels.add(NotificationChannel.EMAIL);
        }
        if (recipient.telegramLinked()) {
            channels.add(NotificationChannel.TELEGRAM);
        }
        return channels;
    }
}
