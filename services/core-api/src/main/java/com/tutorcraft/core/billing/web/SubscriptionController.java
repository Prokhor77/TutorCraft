package com.tutorcraft.core.billing.web;

import com.tutorcraft.core.billing.application.SubscriptionService;
import com.tutorcraft.core.billing.application.SubscriptionView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Подписка школы на платформу (контракт §13.2). */
@RestController
@RequestMapping("/api/v1/billing/subscription")
class SubscriptionController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final int MAX_TERM = 32;

    private final SubscriptionService subscriptions;

    SubscriptionController(SubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    @GetMapping
    SubscriptionView get() {
        return subscriptions.get();
    }

    @PostMapping("/purchases")
    SubscriptionView purchase(@RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
                              @Valid @RequestBody PurchaseRequest request) {
        return subscriptions.purchase(request.term(), idempotencyKey);
    }

    record PurchaseRequest(@NotBlank @Size(max = MAX_TERM) String term) {
    }
}
