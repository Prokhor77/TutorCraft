package com.tutorcraft.core.billing.application;

import com.tutorcraft.core.billing.domain.Order;
import com.tutorcraft.core.billing.domain.OrderStatus;
import com.tutorcraft.core.shared.api.PageQuery;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Заказы и журнал входящих событий провайдеров. */
public interface OrderRepository {

    void insert(Order order);

    void attachPayment(UUID tenantId, UUID orderId, String providerPaymentId, String confirmationUrl);

    Optional<Order> find(UUID tenantId, UUID orderId);

    /** Поиск по id платежа провайдера (вебхук не знает tenant — tenant берётся из найденного заказа). */
    Optional<Order> findByProviderPayment(String provider, String providerPaymentId);

    /** Атомарный переход from → to. @return false — статус уже другой (повторный вебхук, гонка) */
    boolean transition(UUID tenantId, UUID orderId, OrderStatus from, OrderStatus to, Instant paidAt, Instant now);

    /** courseId == null — все заказы tenant. */
    List<Order> list(UUID tenantId, UUID courseId, PageQuery page);

    /** @return false — событие провайдера уже обработано */
    boolean recordPaymentEvent(String provider, String eventId, UUID orderId, Map<String, Object> summary, Instant now);
}
