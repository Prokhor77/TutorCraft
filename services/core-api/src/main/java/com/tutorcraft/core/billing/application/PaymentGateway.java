package com.tutorcraft.core.billing.application;

import com.tutorcraft.core.shared.domain.Money;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;

/**
 * Порт платёжного провайдера (FR-ENROL-09, ADR-002). Реализации: fake (dev), yookassa, stripe — выбирается
 * настройкой {@code tutorcraft.payments.provider}. Секреты не логируются, тела запросов/ответов — тоже.
 */
public interface PaymentGateway {

    String provider();

    /** Создаёт платёж; повтор с тем же idempotenceKey не создаёт второй платёж у провайдера. */
    PaymentSession create(OrderSnapshot order, String returnUrl, String idempotenceKey);

    /** Статус платежа по API провайдера (источник истины для провайдеров без подписи вебхуков). */
    Optional<PaymentStatus> fetchStatus(String providerPaymentId);

    /**
     * Разбор и проверка вебхука.
     * @throws com.tutorcraft.core.shared.domain.UnauthorizedException code {@code billing.webhook_invalid}
     */
    PaymentWebhookEvent parseWebhook(HttpHeaders headers, byte[] body);

    record OrderSnapshot(UUID orderId, UUID tenantId, UUID courseId, String courseTitle, Money amount) {
    }

    record PaymentSession(String providerPaymentId, String confirmationUrl) {
    }

    enum PaymentStatus { PENDING, PAID, FAILED, CANCELED }

    /**
     * Событие провайдера. status == null — вебхуку нельзя доверять статус, его нужно запросить через {@link #fetchStatus};
     * ignored — событие не относится к платежам.
     */
    record PaymentWebhookEvent(String eventId, String providerPaymentId, PaymentStatus status, boolean ignored) {

        public static PaymentWebhookEvent ignoredEvent() {
            return new PaymentWebhookEvent(null, null, null, true);
        }
    }
}
