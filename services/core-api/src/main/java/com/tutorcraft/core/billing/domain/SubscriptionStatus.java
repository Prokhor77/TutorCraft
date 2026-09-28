package com.tutorcraft.core.billing.domain;

/** Состояние подписки школы в момент времени. */
public enum SubscriptionStatus {
    TRIAL("trial"), ACTIVE("active"), EXPIRED("expired");

    private final String key;

    SubscriptionStatus(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public boolean grantsAccess() {
        return this != EXPIRED;
    }
}
