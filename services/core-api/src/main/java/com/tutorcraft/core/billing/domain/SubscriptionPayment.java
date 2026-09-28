package com.tutorcraft.core.billing.domain;

import com.tutorcraft.core.shared.domain.Money;
import java.time.Instant;
import java.util.UUID;

/** Оплата срока подписки: за какой период и через какого провайдера. */
public record SubscriptionPayment(UUID id, UUID tenantId, SubscriptionTerm term, Money amount, String provider,
                                  Instant periodStart, Instant periodEnd, UUID paidBy, Instant createdAt) {
}
