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
     * Счётчик реального времени в открытые вкладки пользователя (WebSocket, docs/events/README.md): событие
     * tc.notify.requested.v1 с {@code channels=["web"]}, {@code category="counter"} и телом {@code {"name","value"}}.
     * In-app уведомление не создаётся. Вызывать внутри транзакции (outbox).
     *
     * @param name  имя счётчика, {@code ^[a-z][a-z0-9_]{0,63}$} (например {@link #GRADING_QUEUE_COUNTER})
     * @param value новое значение
     */
    void pushCounter(UUID tenantId, UUID userId, String name, long value);

    /** Число работ в очереди проверки пользователя (AC-8). */
    String GRADING_QUEUE_COUNTER = "grading_queue";

    /**
     * @param messageCode код сообщения: заголовок = {@code <code>.title}, текст = {@code <code>.body}, необязательный
     *                    текст кнопки ссылки = {@code <code>.action}; args — позиционные {0},{1}
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
