package com.tutorcraft.core.billing.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.billing.application.PaymentGateway.OrderSnapshot;
import com.tutorcraft.core.billing.application.PaymentGateway.PaymentSession;
import com.tutorcraft.core.billing.domain.Order;
import com.tutorcraft.core.courses.CourseRef;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import com.tutorcraft.core.shared.config.AppProperties;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.domain.Validator;
import com.tutorcraft.core.shared.idempotency.IdempotencyService;
import com.tutorcraft.core.shared.idempotency.IdempotencyService.IdempotencyScope;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Покупка курса (FR-ENROL-09): создание заказа и платежа у провайдера, просмотр заказов. */
@Service
public class OrderService {

    static final String CREATE_OPERATION = "order.create";
    private static final String ACTIVE = "active";
    private static final String RETURN_URL_FIELD = "returnUrl";
    private static final int MAX_URL = 2000;

    private final CurrentUserProvider currentUser;
    private final AccessService access;
    private final CoursesApi courses;
    private final EnrollmentApi enrollment;
    private final OrderRepository orders;
    private final PaymentGateway gateway;
    private final OrderViews views;
    private final IdempotencyService idempotency;
    private final AuditLog audit;
    private final Clock clock;
    private final List<String> allowedReturnOrigins;

    OrderService(CurrentUserProvider currentUser, AccessService access, CoursesApi courses, EnrollmentApi enrollment,
                 OrderRepository orders, PaymentGateway gateway, OrderViews views, IdempotencyService idempotency,
                 AuditLog audit, Clock clock, AppProperties properties) {
        this.currentUser = currentUser;
        this.access = access;
        this.courses = courses;
        this.enrollment = enrollment;
        this.orders = orders;
        this.gateway = gateway;
        this.views = views;
        this.idempotency = idempotency;
        this.audit = audit;
        this.clock = clock;
        this.allowedReturnOrigins = List.of(properties.webOrigin(), properties.publicBaseUrl());
    }

    /** Idempotency-Key обязателен: повтор возвращает тот же заказ и ту же ссылку на оплату. */
    @Transactional
    public OrderCreated create(UUID courseId, String returnUrl, String idempotencyKey) {
        CurrentUser user = currentUser.require();
        validateReturnUrl(returnUrl);
        UUID orderId = idempotency.execute(new IdempotencyScope(user.tenantId(), user.userId(), CREATE_OPERATION),
                idempotencyKey, Map.of("courseId", courseId, RETURN_URL_FIELD, returnUrl), UUID.class,
                () -> place(user, courseId, returnUrl));
        Order order = requireOrder(user.tenantId(), orderId);
        return new OrderCreated(order.id(), order.status().key(), order.confirmationUrl());
    }

    @Transactional(readOnly = true)
    public OrderView get(UUID orderId) {
        CurrentUser user = currentUser.require();
        Order order = requireOrder(user.tenantId(), orderId);
        if (!order.buyerId().equals(user.userId())
                && !access.can(Permission.BILLING_MANAGE, AccessContext.course(order.courseId()))) {
            throw new NotFoundException(BillingErrors.ORDER_NOT_FOUND, "Order not found");
        }
        return views.of(order);
    }

    /** Заказы курса (billing.manage в курсе) или всей школы (billing.manage в tenant). */
    @Transactional(readOnly = true)
    public PageResponse<OrderView> list(UUID courseId, PageQuery page) {
        CurrentUser user = currentUser.require();
        access.require(Permission.BILLING_MANAGE, courseId == null ? AccessContext.tenant() : AccessContext.course(courseId));
        PageResponse<Order> result = page.toPage(orders.list(user.tenantId(), courseId, page), Order::createdAt, Order::id);
        return new PageResponse<>(views.of(user.tenantId(), result.items()), result.nextCursor());
    }

    private UUID place(CurrentUser user, UUID courseId, String returnUrl) {
        CourseRef course = courses.requireCourse(user.tenantId(), courseId);
        Instant now = clock.instant();
        if (course.price() == null || course.price().amountMinor() <= 0 || !course.visibility().visibleAt(course.publishAt(), now)) {
            throw new BusinessRuleException(BillingErrors.NOT_FOR_SALE, "Course is not for sale");
        }
        boolean enrolled = enrollment.membership(user.tenantId(), courseId, user.userId())
                .map(member -> ACTIVE.equals(member.status())).orElse(false);
        if (enrolled) {
            throw new BusinessRuleException(BillingErrors.ALREADY_ENROLLED, "Already enrolled in the course");
        }
        Order order = Order.pending(Ids.newId(), user.tenantId(), courseId, user.userId(), course.price(), gateway.provider(), now);
        orders.insert(order);
        PaymentSession session = gateway.create(new OrderSnapshot(order.id(), user.tenantId(), courseId, course.title(),
                course.price()), returnUrl, order.id().toString());
        orders.attachPayment(user.tenantId(), order.id(), session.providerPaymentId(), session.confirmationUrl());
        audit.record(AuditRecord.of(user.tenantId(), user.userId(), "order.created", "order", order.id().toString())
                .withDiff(Map.of("courseId", courseId.toString(), "amountMinor", course.price().amountMinor())));
        return order.id();
    }

    private Order requireOrder(UUID tenantId, UUID orderId) {
        return orders.find(tenantId, orderId)
                .orElseThrow(() -> new NotFoundException(BillingErrors.ORDER_NOT_FOUND, "Order not found"));
    }

    /** Возврат только на свои адреса — защита от open redirect через страницу оплаты. */
    private void validateReturnUrl(String returnUrl) {
        new Validator().notBlank(returnUrl, RETURN_URL_FIELD).maxLength(returnUrl, MAX_URL, RETURN_URL_FIELD)
                .check(returnUrl == null || allowedReturnOrigins.stream().anyMatch(origin -> returnUrl.equals(origin)
                        || returnUrl.startsWith(origin + "/")), RETURN_URL_FIELD, "not_allowed",
                        "Return URL must point to this site")
                .throwIfInvalid();
    }

    public record OrderCreated(UUID orderId, String status, String confirmationUrl) {
    }
}
