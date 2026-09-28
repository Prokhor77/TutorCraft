package com.tutorcraft.core.billing.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorcraft.core.billing.application.BillingErrors;
import com.tutorcraft.core.billing.application.PaymentGateway;
import com.tutorcraft.core.billing.domain.StripeSignature;
import com.tutorcraft.core.shared.config.AppProperties;
import com.tutorcraft.core.shared.domain.UnauthorizedException;
import java.io.IOException;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Stripe Checkout Session. Вебхук проверяется по заголовку Stripe-Signature (HMAC-SHA256, допуск 5 минут);
 * статус берётся из проверенного события.
 */
@Component
@ConditionalOnProperty(name = "tutorcraft.payments.provider", havingValue = "stripe")
class StripeGateway implements PaymentGateway {

    static final String PROVIDER = "stripe";
    private static final String BASE_URL = "https://api.stripe.com/v1";
    private static final String SESSIONS = "/checkout/sessions";
    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final String PAID = "paid";
    private static final String EXPIRED = "expired";
    private static final String COMPLETED = "checkout.session.completed";
    private static final Map<String, PaymentStatus> EVENT_STATUSES = Map.of(
            "checkout.session.async_payment_succeeded", PaymentStatus.PAID,
            "checkout.session.async_payment_failed", PaymentStatus.FAILED,
            "checkout.session.expired", PaymentStatus.CANCELED);

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String webhookSecret;

    StripeGateway(AppProperties properties, ObjectMapper objectMapper, Clock clock) {
        AppProperties.Payments payments = properties.payments();
        PaymentHttp.requireConfigured(payments.stripeSecretKey(), "STRIPE_SECRET_KEY");
        PaymentHttp.requireConfigured(payments.stripeWebhookSecret(), "STRIPE_WEBHOOK_SECRET");
        this.client = PaymentHttp.client(BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + payments.stripeSecretKey())
                .build();
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.webhookSecret = payments.stripeWebhookSecret();
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public PaymentSession create(OrderSnapshot order, String returnUrl, String idempotenceKey) {
        JsonNode session = post(form(order, returnUrl), idempotenceKey);
        String sessionId = session.path("id").asText();
        String url = session.path("url").asText();
        if (sessionId.isBlank() || url.isBlank()) {
            throw PaymentHttp.unavailable();
        }
        return new PaymentSession(sessionId, url);
    }

    @Override
    public Optional<PaymentStatus> fetchStatus(String providerPaymentId) {
        try {
            JsonNode session = client.get().uri(SESSIONS + "/{id}", providerPaymentId).retrieve().body(JsonNode.class);
            return Optional.ofNullable(session).map(StripeGateway::sessionStatus);
        } catch (RestClientException e) {
            throw PaymentHttp.unavailable();
        }
    }

    @Override
    public PaymentWebhookEvent parseWebhook(HttpHeaders headers, byte[] body) {
        String signature = headers.getFirst(StripeSignature.HEADER);
        if (!StripeSignature.verify(signature, body, webhookSecret, clock.instant(), StripeSignature.DEFAULT_TOLERANCE)) {
            throw new UnauthorizedException(BillingErrors.WEBHOOK_INVALID, "Invalid Stripe signature");
        }
        JsonNode event = read(body);
        String type = event.path("type").asText();
        JsonNode session = event.path("data").path("object");
        PaymentStatus status = COMPLETED.equals(type) ? completedStatus(session) : EVENT_STATUSES.get(type);
        if (status == null) {
            return PaymentWebhookEvent.ignoredEvent();
        }
        return new PaymentWebhookEvent(event.path("id").asText(), session.path("id").asText(), status, false);
    }

    private JsonNode post(MultiValueMap<String, String> form, String idempotenceKey) {
        try {
            JsonNode session = client.post().uri(SESSIONS)
                    .header(IDEMPOTENCY_HEADER, idempotenceKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            if (session == null) {
                throw PaymentHttp.unavailable();
            }
            return session;
        } catch (RestClientException e) {
            throw PaymentHttp.unavailable();
        }
    }

    private static MultiValueMap<String, String> form(OrderSnapshot order, String returnUrl) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "payment");
        form.add("success_url", returnUrl);
        form.add("cancel_url", returnUrl);
        form.add("client_reference_id", order.orderId().toString());
        form.add("metadata[orderId]", order.orderId().toString());
        form.add("metadata[tenantId]", order.tenantId().toString());
        form.add("line_items[0][quantity]", "1");
        form.add("line_items[0][price_data][currency]", order.amount().currency().toLowerCase(Locale.ROOT));
        form.add("line_items[0][price_data][unit_amount]", Long.toString(order.amount().amountMinor()));
        form.add("line_items[0][price_data][product_data][name]", order.courseTitle());
        return form;
    }

    /** checkout.session.completed: оплачено сразу или ожидает асинхронного подтверждения. */
    private static PaymentStatus completedStatus(JsonNode session) {
        return PAID.equals(session.path("payment_status").asText()) ? PaymentStatus.PAID : PaymentStatus.PENDING;
    }

    private static PaymentStatus sessionStatus(JsonNode session) {
        if (PAID.equals(session.path("payment_status").asText())) {
            return PaymentStatus.PAID;
        }
        return EXPIRED.equals(session.path("status").asText()) ? PaymentStatus.CANCELED : PaymentStatus.PENDING;
    }

    private JsonNode read(byte[] body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException e) {
            throw new UnauthorizedException(BillingErrors.WEBHOOK_INVALID, "Malformed event");
        }
    }
}
