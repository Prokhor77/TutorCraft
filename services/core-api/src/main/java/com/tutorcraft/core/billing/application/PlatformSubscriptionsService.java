package com.tutorcraft.core.billing.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.billing.domain.TenantSubscription;
import com.tutorcraft.core.org.OrgApi;
import com.tutorcraft.core.org.OrgApi.TenantInfo;
import com.tutorcraft.core.shared.api.IfMatch;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import com.tutorcraft.core.shared.domain.ConflictException;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Подписки всех школ для главного администратора платформы (право platform.manage): сроки пробного и оплаченного
 * доступа и ручная установка конца бесплатного (пробного) доступа — продление или сокращение без оплаты.
 */
@Service
public class PlatformSubscriptionsService {

    static final Duration MAX_TRIAL_AHEAD = Duration.ofDays(5 * 366);
    private static final Logger log = LoggerFactory.getLogger(PlatformSubscriptionsService.class);
    private static final String OBJECT_TYPE = "subscription";
    private static final String TENANT_NOT_FOUND = "tenant.not_found";

    private final CurrentUserProvider currentUser;
    private final AccessService access;
    private final Subscriptions subscriptions;
    private final SubscriptionRepository repository;
    private final OrgApi org;
    private final AuditLog audit;
    private final Clock clock;

    PlatformSubscriptionsService(CurrentUserProvider currentUser, AccessService access, Subscriptions subscriptions,
                                 SubscriptionRepository repository, OrgApi org, AuditLog audit, Clock clock) {
        this.currentUser = currentUser;
        this.access = access;
        this.subscriptions = subscriptions;
        this.repository = repository;
        this.org = org;
        this.audit = audit;
        this.clock = clock;
    }

    /** Школы, у которых пробный период ещё не начался (никто не заходил в кабинет), в список не попадают. */
    @Transactional(readOnly = true)
    public List<PlatformSubscriptionView> list() {
        access.require(Permission.PLATFORM_MANAGE, AccessContext.tenant());
        Instant now = clock.instant();
        return repository.findAll().stream().map(subscription -> view(subscription, now)).toList();
    }

    /**
     * Новый конец бесплатного доступа школы. Оплаченный срок не меняется: доступ действует до позднейшей из дат,
     * а следующая оплата начнётся после неё.
     */
    @Transactional
    public PlatformSubscriptionView setTrialEnd(UUID tenantId, Instant trialEndsAt, long expectedVersion) {
        CurrentUser admin = currentUser.require();
        access.require(Permission.PLATFORM_MANAGE, AccessContext.tenant());
        TenantInfo tenant = org.require(tenantId);
        if (OrgApi.PLATFORM_TENANT_SLUG.equals(tenant.slug())) {
            throw new NotFoundException(TENANT_NOT_FOUND, "Tenant not found");
        }
        Instant now = clock.instant();
        if (!trialEndsAt.isAfter(now) || trialEndsAt.isAfter(now.plus(MAX_TRIAL_AHEAD))) {
            throw new BusinessRuleException(BillingErrors.TRIAL_END_OUT_OF_RANGE, "Trial end is out of range",
                    Map.of("maxDays", MAX_TRIAL_AHEAD.toDays()));
        }
        TenantSubscription current = subscriptions.load(tenantId);
        IfMatch.check(expectedVersion, current.version());
        if (!repository.updateTrialEndsAt(tenantId, trialEndsAt, current.version(), now)) {
            throw new ConflictException(IfMatch.VERSION_CONFLICT_CODE, "Subscription was modified concurrently");
        }
        audit.record(AuditRecord.of(tenantId, admin.userId(), "subscription.trial_changed", OBJECT_TYPE,
                tenantId.toString()).withDiff(Map.of("trialEndsAt", Map.of(
                        "from", current.trialEndsAt().toString(), "to", trialEndsAt.toString()))));
        log.info("Trial of tenant {} set to end at {} by platform administrator {}", tenantId, trialEndsAt,
                admin.userId());
        return view(subscriptions.load(tenantId), now);
    }

    private static PlatformSubscriptionView view(TenantSubscription subscription, Instant now) {
        return new PlatformSubscriptionView(subscription.tenantId(), subscription.statusAt(now).key(),
                subscription.trialEndsAt(), subscription.paidUntil(), subscription.accessUntil(),
                subscription.version());
    }
}
