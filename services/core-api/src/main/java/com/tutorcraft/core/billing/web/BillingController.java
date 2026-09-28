package com.tutorcraft.core.billing.web;

import com.tutorcraft.core.billing.application.OrderService;
import com.tutorcraft.core.billing.application.OrderService.OrderCreated;
import com.tutorcraft.core.billing.application.OrderView;
import com.tutorcraft.core.billing.application.PaymentWebhookService;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Биллинг (контракт §13): заказы, вебхуки провайдеров, имитация оплаты для fake-провайдера. */
@RestController
@RequestMapping("/api/v1")
class BillingController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final int MAX_URL = 2000;

    private final OrderService orders;
    private final PaymentWebhookService payments;

    BillingController(OrderService orders, PaymentWebhookService payments) {
        this.orders = orders;
        this.payments = payments;
    }

    @PostMapping("/courses/{courseId}/orders")
    @ResponseStatus(HttpStatus.CREATED)
    OrderCreated createOrder(@PathVariable UUID courseId, @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
                             @Valid @RequestBody CreateOrderRequest request) {
        return orders.create(courseId, request.returnUrl(), idempotencyKey);
    }

    @GetMapping("/orders/{orderId}")
    OrderView order(@PathVariable UUID orderId) {
        return orders.get(orderId);
    }

    @GetMapping("/billing/orders")
    PageResponse<OrderView> list(@RequestParam(required = false) UUID courseId, @RequestParam(required = false) String cursor,
                                 @RequestParam(required = false) Integer limit) {
        return orders.list(courseId, PageQuery.of(cursor, limit));
    }

    /** Публичный эндпоинт: тело читается байтами — подпись Stripe считается по исходному телу. */
    @PostMapping("/billing/webhooks/{provider}")
    void webhook(@PathVariable String provider, @RequestHeader HttpHeaders headers,
                 @RequestBody(required = false) byte[] body) {
        payments.handle(provider, headers, body == null ? new byte[0] : body);
    }

    @PostMapping("/billing/fake/{orderId}/pay")
    OrderView fakePay(@PathVariable UUID orderId) {
        return payments.fakePay(orderId);
    }

    record CreateOrderRequest(@NotBlank @Size(max = MAX_URL) String returnUrl) {
    }
}
