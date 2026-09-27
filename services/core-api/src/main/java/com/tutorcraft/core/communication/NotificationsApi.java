package com.tutorcraft.core.communication;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Публичный API уведомлений. Текст берётся из i18n по {@code messageCode} на языке получателя.
 * Доставка во внешние каналы — только через outbox → notifier (FR-NOTIF-04). Идемпотентно по dedupeKey.
 * Вызывать внутри транзакции бизнес-операции.
 */
public interface NotificationsApi {

    void notify(NotificationCommand command);

    /**
     * @param messageCode код сообщения: заголовок = {@code <code>.title}, текст = {@code <code>.body}; args — позиционные {0},{1}
     * @param dedupeKey   уникален на событие; уведомление получателю на канал создаётся один раз
     * @param force       true — игнорировать настройки пользователя (служебные: сброс пароля, приглашение)
     * @param directEmail для писем получателям без аккаунта (приглашения); иначе null
     */
    record NotificationCommand(UUID tenantId, Collection<UUID> userIds, NotificationCategory category, String messageCode,
                               List<Object> args, String link, String dedupeKey, boolean force, String directEmail) {

        public static NotificationCommand of(UUID tenantId, Collection<UUID> userIds, NotificationCategory category,
                                             String messageCode, List<Object> args, String link, String dedupeKey) {
            return new NotificationCommand(tenantId, userIds, category, messageCode, args, link, dedupeKey, false, null);
        }
    }
}
