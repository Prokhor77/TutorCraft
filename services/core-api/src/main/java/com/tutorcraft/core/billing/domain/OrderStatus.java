package com.tutorcraft.core.billing.domain;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Статусы заказа и допустимые переходы: pending → paid | failed | canceled; paid → refunded.
 * failed/canceled/refunded — конечные.
 */
public enum OrderStatus {
    PENDING("pending"), PAID("paid"), FAILED("failed"), REFUNDED("refunded"), CANCELED("canceled");

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            PENDING, EnumSet.of(PAID, FAILED, CANCELED),
            PAID, EnumSet.of(REFUNDED),
            FAILED, EnumSet.noneOf(OrderStatus.class),
            REFUNDED, EnumSet.noneOf(OrderStatus.class),
            CANCELED, EnumSet.noneOf(OrderStatus.class));

    private final String key;

    OrderStatus(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public boolean canTransitionTo(OrderStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    public static OrderStatus fromKey(String key) {
        return Arrays.stream(values()).filter(status -> status.key.equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown order status"));
    }
}
