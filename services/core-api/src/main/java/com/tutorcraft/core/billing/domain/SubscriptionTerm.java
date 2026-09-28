package com.tutorcraft.core.billing.domain;

import com.tutorcraft.core.shared.domain.Money;
import com.tutorcraft.core.shared.domain.ValidationException;
import java.util.Arrays;

/** Сроки подписки: функционал одинаковый, отличаются длительность и цена (в центах USD). */
public enum SubscriptionTerm {
    MONTH("month", 1, 4_000),
    QUARTER("quarter", 3, 12_000),
    YEAR("year", 12, 24_000);

    public static final String CURRENCY = "USD";
    private static final String FIELD = "term";

    private final String key;
    private final int months;
    private final long priceMinor;

    SubscriptionTerm(String key, int months, long priceMinor) {
        this.key = key;
        this.months = months;
        this.priceMinor = priceMinor;
    }

    public String key() {
        return key;
    }

    public int months() {
        return months;
    }

    public Money price() {
        return new Money(priceMinor, CURRENCY);
    }

    public static SubscriptionTerm fromKey(String key) {
        return Arrays.stream(values())
                .filter(term -> term.key.equals(key))
                .findFirst()
                .orElseThrow(() -> ValidationException.single(FIELD, "invalid_term", "Unknown subscription term"));
    }
}
