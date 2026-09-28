package com.tutorcraft.core.billing.application;

import com.tutorcraft.core.shared.domain.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Подписка школы для кабинета (контракт §13.2). {@code canManage} — может ли пользователь оплачивать. */
public record SubscriptionView(String status, Instant trialEndsAt, Instant paidUntil, Instant accessUntil,
                               boolean canManage, List<TermView> terms, List<PaymentView> payments) {

    public record TermView(String term, int months, Money price) {
    }

    public record PaymentView(UUID id, String term, Money amount, Instant periodStart, Instant periodEnd,
                              Instant createdAt) {
    }
}
