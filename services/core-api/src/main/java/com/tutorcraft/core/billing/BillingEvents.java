package com.tutorcraft.core.billing;

import java.util.UUID;

public final class BillingEvents {

    private BillingEvents() {
    }

    public record OrderPaid(UUID tenantId, UUID orderId, UUID courseId, UUID buyerId, long amountMinor, String currency) {
    }
}
