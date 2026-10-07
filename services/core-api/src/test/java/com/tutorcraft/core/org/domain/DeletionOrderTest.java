package com.tutorcraft.core.org.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tutorcraft.core.org.domain.DeletionOrder.Reference;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeletionOrderTest {

    @Test
    void referencingTablesGoFirst() {
        List<String> order = DeletionOrder.of(List.of("users", "courses", "enrollments", "grades", "grade_items"), List.of(
                new Reference("enrollments", "users"),
                new Reference("enrollments", "courses"),
                new Reference("courses", "users"),
                new Reference("grades", "grade_items"),
                new Reference("grades", "users")));

        assertThat(order).containsExactlyInAnyOrder("users", "courses", "enrollments", "grades", "grade_items");
        assertThat(order.indexOf("enrollments")).isLessThan(order.indexOf("courses"));
        assertThat(order.indexOf("courses")).isLessThan(order.indexOf("users"));
        assertThat(order.indexOf("grades")).isLessThan(order.indexOf("grade_items"));
        assertThat(order.indexOf("grades")).isLessThan(order.indexOf("users"));
    }

    @Test
    void selfReferenceAndOutsideTablesDoNotBlock() {
        List<String> order = DeletionOrder.of(List.of("users", "categories"), List.of(
                new Reference("users", "users"),
                new Reference("categories", "categories"),
                new Reference("users", "tenants"),
                new Reference("audit_log", "users")));

        assertThat(order).containsExactly("categories", "users");
    }

    @Test
    void cycleBetweenTablesIsReported() {
        assertThatThrownBy(() -> DeletionOrder.of(List.of("a", "b"), List.of(new Reference("a", "b"), new Reference("b", "a"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cycle");
    }
}
