package com.tutorcraft.core.billing.application;

import com.tutorcraft.core.billing.domain.SubscriptionPayment;
import com.tutorcraft.core.billing.domain.TenantSubscription;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SubscriptionRepository {

    /** Подписка tenant; если её ещё нет — сохраняет {@code trial} (конкурентно безопасно) и возвращает сохранённую. */
    TenantSubscription findOrCreate(TenantSubscription trial, Instant now);

    /** Оптимистичная блокировка: false, если версия изменилась. */
    boolean updatePaidUntil(UUID tenantId, Instant paidUntil, long expectedVersion, Instant now);

    void insertPayment(SubscriptionPayment payment);

    /** Последние оплаты tenant, новые первыми. */
    List<SubscriptionPayment> recentPayments(UUID tenantId, int limit);
}
