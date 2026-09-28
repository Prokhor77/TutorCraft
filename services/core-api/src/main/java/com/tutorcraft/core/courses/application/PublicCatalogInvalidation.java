package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.courses.CourseEvents.CourseChanged;
import com.tutorcraft.core.courses.CourseEvents.ItemChanged;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Сброс кэша витрины после фиксации изменений курса или его структуры (не раньше — иначе кэш заполнится старым). */
@Component
class PublicCatalogInvalidation {

    private final PublicCatalogCache cache;

    PublicCatalogInvalidation(PublicCatalogCache cache) {
        this.cache = cache;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onCourseChanged(CourseChanged event) {
        cache.invalidate(event.tenantId());
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onItemChanged(ItemChanged event) {
        cache.invalidate(event.tenantId());
    }
}
