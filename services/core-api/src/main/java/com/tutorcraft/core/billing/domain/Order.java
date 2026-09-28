package com.tutorcraft.core.billing.domain;

import com.tutorcraft.core.shared.domain.Money;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Заказ на покупку курса (FR-ENROL-09). Деньги — в минимальных единицах валюты (ADR-002). */
public record Order(UUID id, UUID tenantId, UUID courseId, UUID buyerId, Money amount, OrderStatus status, String provider,
                    String providerPaymentId, String confirmationUrl, Instant createdAt, Instant paidAt, long version) {

    public static Order pending(UUID id, UUID tenantId, UUID courseId, UUID buyerId, Money amount, String provider,
                                Instant now) {
        return new Order(id, tenantId, courseId, buyerId, amount, OrderStatus.PENDING, provider, null, null, now, null, 0);
    }

    /** Переход статуса; недопустимый (или повторный) переход — пусто, вызывающий код ничего не меняет. */
    public Optional<Order> transitionTo(OrderStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            return Optional.empty();
        }
        Instant paid = target == OrderStatus.PAID ? now : paidAt;
        return Optional.of(new Order(id, tenantId, courseId, buyerId, amount, target, provider, providerPaymentId,
                confirmationUrl, createdAt, paid, version));
    }
}
