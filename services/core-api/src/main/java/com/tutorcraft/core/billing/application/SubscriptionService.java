package com.tutorcraft.core.billing.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.billing.application.SubscriptionView.PaymentView;
import com.tutorcraft.core.billing.application.SubscriptionView.TermView;
import com.tutorcraft.core.billing.domain.SubscriptionPayment;
import com.tutorcraft.core.billing.domain.SubscriptionTerm;
import com.tutorcraft.core.billing.domain.TenantSubscription;
import com.tutorcraft.core.shared.api.IfMatch;
import com.tutorcraft.core.shared.config.AppProperties;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import com.tutorcraft.core.shared.domain.ConflictException;
import com.tutorcraft.core.shared.domain.ForbiddenException;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.idempotency.IdempotencyService;
import com.tutorcraft.core.shared.idempotency.IdempotencyService.IdempotencyScope;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Подписка школы в кабинете: статус, сроки, история оплат и покупка срока.
 * Пока платёжный провайдер — fake: покупка активирует срок сразу, без перехода на страницу оплаты.
 * Это единственный платёж на платформе: курсы для учеников бесплатны (ADR-012).
 */
@Service
public class SubscriptionService {

    static final String PURCHASE_OPERATION = "subscription.purchase";
    static final String FAKE_PROVIDER = "fake";
    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);
    private static final int PAYMENTS_SHOWN = 20;
    private static final String OBJECT_TYPE = "subscription";
    private static final String ACCESS_DENIED = "access.denied";

    private final CurrentUserProvider currentUser;
    private final AccessService access;
    private final Subscriptions subscriptions;
    private final SubscriptionRepository repository;
    private final String paymentProvider;
    private final IdempotencyService idempotency;
    private final AuditLog audit;
    private final Clock clock;

    SubscriptionService(CurrentUserProvider currentUser, AccessService access, Subscriptions subscriptions,
                        SubscriptionRepository repository, AppProperties properties, IdempotencyService idempotency,
                        AuditLog audit, Clock clock) {
        this.currentUser = currentUser;
        this.access = access;
        this.subscriptions = subscriptions;
        this.repository = repository;
        this.paymentProvider = properties.payments().provider();
        this.idempotency = idempotency;
        this.audit = audit;
        this.clock = clock;
    }

    /** Видят сотрудники школы (создатели курсов и владелец); оплачивать может только billing.manage. */
    @Transactional
    public SubscriptionView get() {
        CurrentUser user = currentUser.require();
        boolean canManage = access.can(Permission.BILLING_MANAGE, AccessContext.tenant());
        if (!canManage && !access.can(Permission.COURSE_CREATE, AccessContext.tenant())) {
            throw new ForbiddenException(ACCESS_DENIED, "Missing permission billing.manage",
                    Map.of("permission", Permission.BILLING_MANAGE.key()));
        }
        return view(subscriptions.load(user.tenantId()), canManage, user);
    }

    /** Idempotency-Key обязателен: повтор запроса не продлевает подписку второй раз. */
    @Transactional
    public SubscriptionView purchase(String termKey, String idempotencyKey) {
        CurrentUser user = currentUser.require();
        access.require(Permission.BILLING_MANAGE, AccessContext.tenant());
        SubscriptionTerm term = SubscriptionTerm.fromKey(termKey);
        requireInstantCheckout();
        idempotency.execute(new IdempotencyScope(user.tenantId(), user.userId(), PURCHASE_OPERATION), idempotencyKey,
                Map.of("term", term.key()), String.class, () -> activate(user, term));
        return view(subscriptions.load(user.tenantId()), true, user);
    }

    private String activate(CurrentUser user, SubscriptionTerm term) {
        Instant now = clock.instant();
        TenantSubscription current = subscriptions.load(user.tenantId());
        Instant start = current.nextPeriodStart(now);
        Instant end = TenantSubscription.periodEnd(start, term);
        if (!repository.updatePaidUntil(user.tenantId(), end, current.version(), now)) {
            throw new ConflictException(IfMatch.VERSION_CONFLICT_CODE, "Subscription was modified concurrently");
        }
        SubscriptionPayment payment = new SubscriptionPayment(Ids.newId(), user.tenantId(), term, term.price(),
                paymentProvider, start, end, user.userId(), now);
        repository.insertPayment(payment);
        audit.record(AuditRecord.of(user.tenantId(), user.userId(), "subscription.purchased", OBJECT_TYPE,
                user.tenantId().toString()).withDiff(Map.of("term", term.key(), "paidUntil", end.toString())));
        log.info("Subscription of tenant {} extended by {} until {}", user.tenantId(), term.key(), end);
        return payment.id().toString();
    }

    /** Мгновенная активация допустима только с fake-провайдером; реальная оплата подключается отдельно. */
    private void requireInstantCheckout() {
        if (!FAKE_PROVIDER.equals(paymentProvider)) {
            throw new BusinessRuleException(BillingErrors.SUBSCRIPTION_CHECKOUT_UNAVAILABLE,
                    "Subscription checkout is not available for provider " + paymentProvider);
        }
    }

    private SubscriptionView view(TenantSubscription subscription, boolean canManage, CurrentUser user) {
        List<TermView> terms = Arrays.stream(SubscriptionTerm.values())
                .map(term -> new TermView(term.key(), term.months(), term.price()))
                .toList();
        List<PaymentView> payments = canManage ? payments(user) : List.of();
        return new SubscriptionView(subscription.statusAt(clock.instant()).key(), subscription.trialEndsAt(),
                subscription.paidUntil(), subscription.accessUntil(), canManage, terms, payments);
    }

    private List<PaymentView> payments(CurrentUser user) {
        return repository.recentPayments(user.tenantId(), PAYMENTS_SHOWN).stream()
                .map(p -> new PaymentView(p.id(), p.term().key(), p.amount(), p.periodStart(), p.periodEnd(),
                        p.createdAt()))
                .toList();
    }
}
