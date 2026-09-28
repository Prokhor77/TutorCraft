package com.tutorcraft.core.billing;

import java.util.UUID;

/**
 * Подписка школы (tenant) на платформу. Все сроки открывают одинаковый функционал; без активной подписки
 * (пробный период истёк, оплаченный срок закончился) школа работает только на чтение: нельзя создавать
 * и публиковать курсы. Ученики продолжают учиться.
 */
public interface SubscriptionApi {

    /**
     * @throws com.tutorcraft.core.shared.domain.BusinessRuleException code {@code billing.subscription_inactive}
     */
    void requireActive(UUID tenantId);
}
