package com.tutorcraft.core.billing.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorcraft.core.billing.application.BillingErrors;
import com.tutorcraft.core.billing.application.PaymentGateway;
import com.tutorcraft.core.billing.domain.MoneyFormatter;
import com.tutorcraft.core.shared.config.AppProperties;
import com.tutorcraft.core.shared.domain.UnauthorizedException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * ЮKassa API v3: платёж с подтверждением redirect. У уведомлений ЮKassa нет подписи — статус платежа всегда
 * перепроверяется запросом GET /payments/{id} (вебхук служит только сигналом).
 */
@Component
@ConditionalOnProperty(name = "tutorcraft.payments.provider", havingValue = "yookassa")
class YooKassaGateway implements PaymentGateway {

    static final String PROVIDER = "yookassa";
    private static final String BASE_URL = "https://api.yookassa.ru/v3";
    private static final String PAYMENTS = "/payments";
    private static final String IDEMPOTENCE_HEADER = "Idempotence-Key";
    private static final String CONFIRMATION_REDIRECT = "redirect";
    private static final String PAYMENT_EVENT_PREFIX = "payment.";
    private static final String EVENT_ID_SEPARATOR = ":";
    private static final int MAX_DESCRIPTION = 128;
    private static final Map<String, PaymentStatus> STATUSES = Map.of(
            "pending", PaymentStatus.PENDING, "waiting_for_capture", PaymentStatus.PENDING,
            "succeeded", PaymentStatus.PAID, "canceled", PaymentStatus.CANCELED);

    private final RestClient client;
    private final ObjectMapper objectMapper;

    YooKassaGateway(AppProperties properties, ObjectMapper objectMapper) {
        AppProperties.Payments payments = properties.payments();
        PaymentHttp.requireConfigured(payments.yookassaShopId(), "YOOKASSA_SHOP_ID");
        PaymentHttp.requireConfigured(payments.yookassaSecretKey(), "YOOKASSA_SECRET_KEY");
        String credentials = payments.yookassaShopId() + ":" + payments.yookassaSecretKey();
        this.client = PaymentHttp.client(BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                        "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)))
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public PaymentSession create(OrderSnapshot order, String returnUrl, String idempotenceKey) {
        JsonNode payment = post(paymentRequest(order, returnUrl), idempotenceKey);
        String paymentId = payment.path("id").asText();
        String confirmationUrl = payment.path("confirmation").path("confirmation_url").asText();
        if (paymentId.isBlank() || confirmationUrl.isBlank()) {
            throw PaymentHttp.unavailable();
        }
        return new PaymentSession(paymentId, confirmationUrl);
    }

    private JsonNode post(Map<String, Object> request, String idempotenceKey) {
        try {
            JsonNode payment = client.post().uri(PAYMENTS)
                    .header(IDEMPOTENCE_HEADER, idempotenceKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            if (payment == null) {
                throw PaymentHttp.unavailable();
            }
            return payment;
        } catch (RestClientException e) {
            throw PaymentHttp.unavailable();
        }
    }

    @Override
    public Optional<PaymentStatus> fetchStatus(String providerPaymentId) {
        try {
            JsonNode payment = client.get().uri(PAYMENTS + "/{id}", providerPaymentId).retrieve().body(JsonNode.class);
            return Optional.ofNullable(payment).map(node -> STATUSES.get(node.path("status").asText()));
        } catch (RestClientException e) {
            throw PaymentHttp.unavailable();
        }
    }

    @Override
    public PaymentWebhookEvent parseWebhook(HttpHeaders headers, byte[] body) {
        JsonNode notification = read(body);
        String event = notification.path("event").asText();
        String paymentId = notification.path("object").path("id").asText();
        if (!event.startsWith(PAYMENT_EVENT_PREFIX) || paymentId.isBlank()) {
            return PaymentWebhookEvent.ignoredEvent();
        }
        return new PaymentWebhookEvent(paymentId + EVENT_ID_SEPARATOR + event, paymentId, null, false);
    }

    private Map<String, Object> paymentRequest(OrderSnapshot order, String returnUrl) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("amount", Map.of("value", MoneyFormatter.toDecimal(order.amount()).toPlainString(),
                "currency", order.amount().currency()));
        request.put("capture", true);
        request.put("confirmation", Map.of("type", CONFIRMATION_REDIRECT, "return_url", returnUrl));
        request.put("description", truncate(order.courseTitle()));
        request.put("metadata", Map.of("orderId", order.orderId().toString(), "tenantId", order.tenantId().toString()));
        return request;
    }

    private JsonNode read(byte[] body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException e) {
            throw new UnauthorizedException(BillingErrors.WEBHOOK_INVALID, "Malformed notification");
        }
    }

    private static String truncate(String text) {
        return text.length() <= MAX_DESCRIPTION ? text : text.substring(0, MAX_DESCRIPTION);
    }
}
