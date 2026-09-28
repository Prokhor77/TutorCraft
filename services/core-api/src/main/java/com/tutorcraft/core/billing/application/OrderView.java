package com.tutorcraft.core.billing.application;

import com.tutorcraft.core.shared.domain.Money;
import java.time.Instant;
import java.util.UUID;

/** Заказ (контракт §13 Order). */
public record OrderView(UUID id, UUID courseId, String courseTitle, UUID buyerId, String buyerName, Money amount,
                        String status, String provider, Instant createdAt, Instant paidAt) {
}
