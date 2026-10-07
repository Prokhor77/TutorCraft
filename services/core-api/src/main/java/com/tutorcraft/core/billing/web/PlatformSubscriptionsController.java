package com.tutorcraft.core.billing.web;

import com.tutorcraft.core.billing.application.PlatformSubscriptionView;
import com.tutorcraft.core.billing.application.PlatformSubscriptionsService;
import com.tutorcraft.core.shared.api.IfMatch;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Подписки школ — эндпоинты главного администратора платформы (контракт §13.3). */
@RestController
@RequestMapping("/api/v1/platform/subscriptions")
class PlatformSubscriptionsController {

    private final PlatformSubscriptionsService subscriptions;

    PlatformSubscriptionsController(PlatformSubscriptionsService subscriptions) {
        this.subscriptions = subscriptions;
    }

    @GetMapping
    List<PlatformSubscriptionView> list() {
        return subscriptions.list();
    }

    @PutMapping("/{tenantId}/trial")
    PlatformSubscriptionView setTrialEnd(@PathVariable UUID tenantId,
                                         @RequestHeader(value = IfMatch.HEADER, required = false) String ifMatch,
                                         @Valid @RequestBody TrialRequest request) {
        return subscriptions.setTrialEnd(tenantId, request.trialEndsAt(), IfMatch.resolve(ifMatch, request.version()));
    }

    record TrialRequest(@NotNull Instant trialEndsAt, Long version) {
    }
}
