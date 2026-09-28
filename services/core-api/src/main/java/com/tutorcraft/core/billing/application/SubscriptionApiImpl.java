package com.tutorcraft.core.billing.application;

import com.tutorcraft.core.billing.SubscriptionApi;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class SubscriptionApiImpl implements SubscriptionApi {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionApiImpl.class);

    private final Subscriptions subscriptions;
    private final Clock clock;

    SubscriptionApiImpl(Subscriptions subscriptions, Clock clock) {
        this.subscriptions = subscriptions;
        this.clock = clock;
    }

    @Override
    public void requireActive(UUID tenantId) {
        if (subscriptions.load(tenantId).statusAt(clock.instant()).grantsAccess()) {
            return;
        }
        log.info("Write operation rejected: subscription of tenant {} is inactive", tenantId);
        throw new BusinessRuleException(BillingErrors.SUBSCRIPTION_INACTIVE, "Subscription is inactive");
    }
}
