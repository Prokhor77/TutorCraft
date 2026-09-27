package com.tutorcraft.core.shared.domain;

import java.util.Currency;

/** Денежная сумма в минимальных единицах валюты (копейки/центы), ISO-4217. */
public record Money(long amountMinor, String currency) {

    public Money {
        if (amountMinor < 0) {
            throw ValidationException.single("amountMinor", "negative", "Amount must not be negative");
        }
        if (currency == null || !isKnownCurrency(currency)) {
            throw ValidationException.single("currency", "invalid_currency", "Unknown currency");
        }
    }

    private static boolean isKnownCurrency(String code) {
        try {
            Currency.getInstance(code);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
