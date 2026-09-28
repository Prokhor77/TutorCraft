package com.tutorcraft.core.billing.application;

import com.tutorcraft.core.billing.domain.Order;
import com.tutorcraft.core.courses.CourseRef;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.identity.UsersApi;
import com.tutorcraft.core.identity.UsersApi.UserRef;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Представление заказа (контракт §13 Order) с названием курса и именем покупателя. */
@Component
class OrderViews {

    private final CoursesApi courses;
    private final UsersApi users;

    OrderViews(CoursesApi courses, UsersApi users) {
        this.courses = courses;
        this.users = users;
    }

    List<OrderView> of(UUID tenantId, List<Order> orders) {
        Map<UUID, CourseRef> courseRefs = courses.findCourses(tenantId, orders.stream().map(Order::courseId).distinct().toList());
        Map<UUID, UserRef> buyers = users.findAll(tenantId, orders.stream().map(Order::buyerId).distinct().toList());
        return orders.stream().map(order -> new OrderView(order.id(), order.courseId(),
                courseRefs.containsKey(order.courseId()) ? courseRefs.get(order.courseId()).title() : null, order.buyerId(),
                buyers.containsKey(order.buyerId()) ? buyers.get(order.buyerId()).displayName() : null, order.amount(),
                order.status().key(), order.provider(), order.createdAt(), order.paidAt())).toList();
    }

    OrderView of(Order order) {
        return of(order.tenantId(), List.of(order)).get(0);
    }
}
