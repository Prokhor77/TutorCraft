package com.tutorcraft.core.billing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.tutorcraft.core.shared.domain.Money;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrderStateMachineTest {

    private static final Instant CREATED = Instant.parse("2026-09-27T10:00:00Z");
    private static final Instant PAID_AT = Instant.parse("2026-09-27T10:05:00Z");

    @ParameterizedTest
    @CsvSource({
        "PENDING, PAID, true", "PENDING, FAILED, true", "PENDING, CANCELED, true", "PENDING, REFUNDED, false",
        "PAID, REFUNDED, true", "PAID, PAID, false", "PAID, CANCELED, false", "PAID, FAILED, false",
        "FAILED, PAID, false", "CANCELED, PAID, false", "REFUNDED, PAID, false", "PENDING, PENDING, false"})
    void transitions(OrderStatus from, OrderStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @Test
    void payingSetsPaidAtOnceAndRepeatedPaymentIsNoop() {
        Order pending = order();

        Order paid = pending.transitionTo(OrderStatus.PAID, PAID_AT).orElseThrow();

        assertThat(paid.status()).isEqualTo(OrderStatus.PAID);
        assertThat(paid.paidAt()).isEqualTo(PAID_AT);
        assertThat(paid.transitionTo(OrderStatus.PAID, PAID_AT.plusSeconds(60))).isEmpty();
        assertThat(paid.transitionTo(OrderStatus.REFUNDED, PAID_AT.plusSeconds(60)).orElseThrow().paidAt()).isEqualTo(PAID_AT);
    }

    @Test
    void failedOrderCannotBePaidLater() {
        Order failed = order().transitionTo(OrderStatus.FAILED, PAID_AT).orElseThrow();

        assertThat(failed.paidAt()).isNull();
        assertThat(failed.transitionTo(OrderStatus.PAID, PAID_AT)).isEmpty();
    }

    @Test
    void statusKeysRoundTrip() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(OrderStatus.fromKey(status.key())).isEqualTo(status);
        }
    }

    @Test
    void moneyIsFormattedForPeople() {
        String rub = MoneyFormatter.format(new Money(500_000, "RUB"), Locale.forLanguageTag("ru"))
                .replace(' ', ' ').replace(' ', ' ');

        assertThat(rub).isEqualTo("5 000,00 ₽");
        assertThat(MoneyFormatter.format(new Money(1999, "USD"), Locale.US)).isEqualTo("$19.99");
        assertThat(MoneyFormatter.toDecimal(new Money(500_000, "RUB")).toPlainString()).isEqualTo("5000.00");
        assertThat(MoneyFormatter.toDecimal(new Money(1500, "JPY")).toPlainString()).isEqualTo("1500");
    }

    private static Order order() {
        return Order.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new Money(500_000, "RUB"), "fake", CREATED);
    }
}
