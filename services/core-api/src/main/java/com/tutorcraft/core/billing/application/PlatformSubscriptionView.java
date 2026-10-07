package com.tutorcraft.core.billing.application;

import java.time.Instant;
import java.util.UUID;

/** Подписка школы для главного администратора платформы. {@code version} — для If-Match при продлении. */
public record PlatformSubscriptionView(UUID tenantId, String status, Instant trialEndsAt, Instant paidUntil,
                                       Instant accessUntil, long version) {
}
