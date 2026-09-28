package com.tutorcraft.core.billing.application;

import com.tutorcraft.core.billing.domain.TenantSubscription;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Загрузка подписки с ленивым стартом пробного периода. Зависит только от репозитория и часов (правило циклов). */
@Component
class Subscriptions {

    private final SubscriptionRepository repository;
    private final Clock clock;

    Subscriptions(SubscriptionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    TenantSubscription load(UUID tenantId) {
        return repository.findOrCreate(TenantSubscription.startTrial(tenantId, clock.instant()), clock.instant());
    }
}
