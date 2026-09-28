package com.tutorcraft.core.billing.domain;

import com.tutorcraft.core.shared.domain.Money;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

/** Сумма для людей (уведомления о продаже): 500000 RUB → «5 000,00 ₽» (ru); десятичная строка для API провайдеров. */
public final class MoneyFormatter {

    private MoneyFormatter() {
    }

    public static String format(Money money, Locale locale) {
        Currency currency = Currency.getInstance(money.currency());
        NumberFormat format = NumberFormat.getCurrencyInstance(locale);
        format.setCurrency(currency);
        int digits = Math.max(currency.getDefaultFractionDigits(), 0);
        format.setMinimumFractionDigits(digits);
        format.setMaximumFractionDigits(digits);
        return format.format(toDecimal(money));
    }

    /** Сумма в основных единицах: 500000 копеек → 5000.00. */
    public static BigDecimal toDecimal(Money money) {
        int digits = Math.max(Currency.getInstance(money.currency()).getDefaultFractionDigits(), 0);
        return BigDecimal.valueOf(money.amountMinor(), digits);
    }
}
