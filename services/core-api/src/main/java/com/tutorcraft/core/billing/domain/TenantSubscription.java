package com.tutorcraft.core.billing.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Подписка школы: пробный период с первого обращения и оплаченный срок. Продление складывается с оставшимся
 * доступом (покупка во время пробного периода или действующей подписки не «сгорает»).
 */
public record TenantSubscription(UUID tenantId, Instant trialEndsAt, Instant paidUntil, long version) {

    public static final Duration TRIAL = Duration.ofDays(14);

    public static TenantSubscription startTrial(UUID tenantId, Instant now) {
        return new TenantSubscription(tenantId, now.plus(TRIAL), null, 0);
    }

    /** Момент, до которого у школы есть доступ (позднейший из пробного и оплаченного срока). */
    public Instant accessUntil() {
        if (paidUntil == null || paidUntil.isBefore(trialEndsAt)) {
            return trialEndsAt;
        }
        return paidUntil;
    }

    public SubscriptionStatus statusAt(Instant now) {
        if (paidUntil != null && now.isBefore(paidUntil)) {
            return SubscriptionStatus.ACTIVE;
        }
        return now.isBefore(trialEndsAt) ? SubscriptionStatus.TRIAL : SubscriptionStatus.EXPIRED;
    }

    /** Начало нового оплаченного периода: сейчас или конец текущего доступа, если он ещё действует. */
    public Instant nextPeriodStart(Instant now) {
        Instant access = accessUntil();
        return access.isAfter(now) ? access : now;
    }

    /** Конец периода длиной {@code term} от {@code start}; календарные месяцы в UTC. */
    public static Instant periodEnd(Instant start, SubscriptionTerm term) {
        return start.atOffset(ZoneOffset.UTC).plusMonths(term.months()).toInstant();
    }
}
