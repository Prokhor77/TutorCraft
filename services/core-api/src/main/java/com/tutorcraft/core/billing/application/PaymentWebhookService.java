package com.tutorcraft.core.billing.application;

import com.tutorcraft.core.billing.application.PaymentGateway.PaymentStatus;
import com.tutorcraft.core.billing.application.PaymentGateway.PaymentWebhookEvent;
import com.tutorcraft.core.billing.domain.Order;
import com.tutorcraft.core.billing.domain.OrderStatus;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Подтверждение оплаты: вебхуки провайдера (идемпотентно по id события) и имитация оплаты для fake-провайдера.
 * Тело вебхука не логируется и не сохраняется целиком (может содержать ПДн) — только сводка.
 */
@Service
public class PaymentWebhookService {

    static final String FAKE_PROVIDER = "fake";
    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

    private final PaymentGateway gateway;
    private final OrderRepository orders;
    private final OrderFulfillment fulfillment;
    private final OrderViews views;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    PaymentWebhookService(PaymentGateway gateway, OrderRepository orders, OrderFulfillment fulfillment, OrderViews views,
                          CurrentUserProvider currentUser, Clock clock) {
        this.gateway = gateway;
        this.orders = orders;
        this.fulfillment = fulfillment;
        this.views = views;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional
    public void handle(String provider, HttpHeaders headers, byte[] body) {
        if (!gateway.provider().equals(provider)) {
            throw new NotFoundException(BillingErrors.PROVIDER_NOT_FOUND, "Payment provider is not configured");
        }
        PaymentWebhookEvent event = gateway.parseWebhook(headers, body);
        if (event.ignored()) {
            return;
        }
        Optional<Order> order = orders.findByProviderPayment(provider, event.providerPaymentId());
        if (!orders.recordPaymentEvent(provider, event.eventId(), order.map(Order::id).orElse(null), summary(event),
                clock.instant())) {
            log.debug("Duplicate {} webhook event ignored", provider);
            return;
        }
        if (order.isEmpty()) {
            log.warn("{} webhook for unknown payment ignored", provider);
            return;
        }
        PaymentStatus status = event.status() != null ? event.status()
                : gateway.fetchStatus(event.providerPaymentId()).orElse(PaymentStatus.PENDING);
        apply(order.get(), status);
    }

    /** POST /billing/fake/{orderId}/pay: только для fake-провайдера и только покупатель. */
    @Transactional
    public OrderView fakePay(UUID orderId) {
        if (!FAKE_PROVIDER.equals(gateway.provider())) {
            throw new NotFoundException(BillingErrors.FAKE_DISABLED, "Fake payments are disabled");
        }
        CurrentUser user = currentUser.require();
        Order order = orders.find(user.tenantId(), orderId)
                .filter(found -> found.buyerId().equals(user.userId()))
                .orElseThrow(() -> new NotFoundException(BillingErrors.ORDER_NOT_FOUND, "Order not found"));
        fulfillment.markPaid(order);
        return views.of(orders.find(user.tenantId(), orderId).orElse(order));
    }

    private void apply(Order order, PaymentStatus status) {
        switch (status) {
            case PAID -> fulfillment.markPaid(order);
            case FAILED -> fulfillment.markClosed(order, OrderStatus.FAILED);
            case CANCELED -> fulfillment.markClosed(order, OrderStatus.CANCELED);
            case PENDING -> log.debug("Order {} payment still pending", order.id());
        }
    }

    private static Map<String, Object> summary(PaymentWebhookEvent event) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("eventId", event.eventId());
        summary.put("paymentId", event.providerPaymentId());
        summary.put("status", event.status() == null ? null : event.status().name());
        return summary;
    }
}
