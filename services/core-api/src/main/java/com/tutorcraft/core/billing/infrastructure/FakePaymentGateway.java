package com.tutorcraft.core.billing.infrastructure;

import com.tutorcraft.core.billing.application.BillingErrors;
import com.tutorcraft.core.billing.application.PaymentGateway;
import com.tutorcraft.core.shared.config.AppProperties;
import com.tutorcraft.core.shared.domain.UnauthorizedException;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Провайдер для разработки: ссылка ведёт на страницу имитации оплаты во фронтенде, оплата подтверждается
 * {@code POST /billing/fake/{orderId}/pay}. Вебхуков нет.
 */
@Component
@ConditionalOnProperty(name = "tutorcraft.payments.provider", havingValue = "fake", matchIfMissing = true)
class FakePaymentGateway implements PaymentGateway {

    static final String PROVIDER = "fake";
    private static final String CHECKOUT_PATH = "/checkout/fake/";
    private static final String PAYMENT_PREFIX = "fake_";

    private final String publicBaseUrl;

    FakePaymentGateway(AppProperties properties) {
        this.publicBaseUrl = properties.publicBaseUrl();
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public PaymentSession create(OrderSnapshot order, String returnUrl, String idempotenceKey) {
        return new PaymentSession(PAYMENT_PREFIX + order.orderId(), publicBaseUrl + CHECKOUT_PATH + order.orderId());
    }

    @Override
    public Optional<PaymentStatus> fetchStatus(String providerPaymentId) {
        return Optional.empty();
    }

    @Override
    public PaymentWebhookEvent parseWebhook(HttpHeaders headers, byte[] body) {
        throw new UnauthorizedException(BillingErrors.WEBHOOK_INVALID, "Fake provider does not accept webhooks");
    }
}
