package com.tutorcraft.core.billing.application;

import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.billing.BillingEvents.OrderPaid;
import com.tutorcraft.core.billing.domain.MoneyFormatter;
import com.tutorcraft.core.billing.domain.Order;
import com.tutorcraft.core.billing.domain.OrderStatus;
import com.tutorcraft.core.communication.NotificationCategory;
import com.tutorcraft.core.communication.NotificationsApi;
import com.tutorcraft.core.communication.NotificationsApi.NotificationCommand;
import com.tutorcraft.core.courses.CourseRef;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.enrollment.EnrollmentApi;
import com.tutorcraft.core.enrollment.EnrollmentApi.EnrolCommand;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Применение результата оплаты. Переход pending → paid атомарен: повторный вебхук ничего не делает.
 * После оплаты: запись на курс (method=payment), событие OrderPaid, уведомление о продаже, аудит.
 */
@Component
class OrderFulfillment {

    static final String SALE_MESSAGE = "billing.sale";
    private static final Logger log = LoggerFactory.getLogger(OrderFulfillment.class);
    private static final Locale MONEY_LOCALE = Locale.forLanguageTag("ru");
    private static final String SALE_DEDUPE_PREFIX = "sale:";
    private static final String ORDERS_LINK_TEMPLATE = "/courses/%s/orders";
    private static final String OBJECT_TYPE = "order";

    private final OrderRepository orders;
    private final EnrollmentApi enrollment;
    private final CoursesApi courses;
    private final NotificationsApi notifications;
    private final ApplicationEventPublisher events;
    private final AuditLog audit;
    private final Clock clock;

    OrderFulfillment(OrderRepository orders, EnrollmentApi enrollment, CoursesApi courses, NotificationsApi notifications,
                     ApplicationEventPublisher events, AuditLog audit, Clock clock) {
        this.orders = orders;
        this.enrollment = enrollment;
        this.courses = courses;
        this.notifications = notifications;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    /** @return true — заказ переведён в paid этим вызовом */
    boolean markPaid(Order order) {
        Instant now = clock.instant();
        if (!order.status().canTransitionTo(OrderStatus.PAID)
                || !orders.transition(order.tenantId(), order.id(), OrderStatus.PENDING, OrderStatus.PAID, now, now)) {
            return false;
        }
        enrollment.enrol(new EnrolCommand(order.tenantId(), order.courseId(), order.buyerId(), CourseRole.STUDENT,
                EnrolCommand.METHOD_PAYMENT, order.buyerId()));
        events.publishEvent(new OrderPaid(order.tenantId(), order.id(), order.courseId(), order.buyerId(),
                order.amount().amountMinor(), order.amount().currency()));
        notifySale(order);
        audit.record(AuditRecord.of(order.tenantId(), order.buyerId(), "order.paid", OBJECT_TYPE, order.id().toString())
                .withDiff(Map.of("amountMinor", order.amount().amountMinor(), "currency", order.amount().currency())));
        log.info("Order {} paid, buyer enrolled into course {}", order.id(), order.courseId());
        return true;
    }

    /** Неуспешный исход (failed/canceled) — только из pending. */
    void markClosed(Order order, OrderStatus target) {
        if (!order.status().canTransitionTo(target)) {
            return;
        }
        Instant now = clock.instant();
        if (orders.transition(order.tenantId(), order.id(), OrderStatus.PENDING, target, null, now)) {
            audit.record(AuditRecord.of(order.tenantId(), order.buyerId(), "order." + target.key(), OBJECT_TYPE,
                    order.id().toString()));
        }
    }

    /** «Новая продажа курса «…» на 5 000,00 ₽!» — преподавателям курса и автору (Telegram по умолчанию включён). */
    private void notifySale(Order order) {
        CourseRef course = courses.requireCourse(order.tenantId(), order.courseId());
        Set<UUID> recipients = new LinkedHashSet<>();
        enrollment.activeMembers(order.tenantId(), order.courseId(), Set.of(CourseRole.TEACHER))
                .forEach(member -> recipients.add(member.userId()));
        if (course.createdBy() != null) {
            recipients.add(course.createdBy());
        }
        notifications.notify(NotificationCommand.of(order.tenantId(), recipients, NotificationCategory.SALE, SALE_MESSAGE,
                List.<Object>of(course.title(), MoneyFormatter.format(order.amount(), MONEY_LOCALE)),
                ORDERS_LINK_TEMPLATE.formatted(order.courseId()), SALE_DEDUPE_PREFIX + order.id()));
    }
}
