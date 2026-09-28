package com.tutorcraft.core.billing.infrastructure;

import com.tutorcraft.core.billing.application.BillingErrors;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** HTTP-клиент к API провайдеров: таймауты, ошибки без тел ответов (могут содержать ПДн/секреты). */
final class PaymentHttp {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(20);

    private PaymentHttp() {
    }

    static RestClient.Builder client(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        factory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory);
    }

    static BusinessRuleException unavailable() {
        return new BusinessRuleException(BillingErrors.PROVIDER_UNAVAILABLE, "Payment provider is unavailable, try again later");
    }

    static void requireConfigured(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured for the selected payment provider");
        }
    }
}
