package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.courses.application.CourseRepository.TenantCourseId;
import com.tutorcraft.core.courses.spi.CourseDataOwner;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Физическое удаление одного курса из корзины (FR-COURSE-07, ADR-010). Курс с данными учащихся или финансовыми
 * данными (любой {@link CourseDataOwner#retainsCourse}) не удаляется — он хранится до анонимизации (FR-USER-05).
 * Порядок: служебные данные модулей → строка курса → структура в MongoDB; ошибка MongoDB откатывает транзакцию
 * PostgreSQL, и курс будет обработан при следующем запуске.
 */
@Component
class CoursePurger {

    private final List<CourseDataOwner> owners;
    private final CourseRepository courses;
    private final ModuleRepository modules;
    private final ItemRepository items;

    CoursePurger(List<CourseDataOwner> owners, CourseRepository courses, ModuleRepository modules, ItemRepository items) {
        this.owners = List.copyOf(owners);
        this.courses = courses;
        this.modules = modules;
        this.items = items;
    }

    /** @return true — курс удалён; false — удерживается данными модулей или уже удалён */
    @Transactional
    public boolean purgeIfUnreferenced(TenantCourseId course) {
        if (owners.stream().anyMatch(owner -> owner.retainsCourse(course.tenantId(), course.courseId()))) {
            return false;
        }
        owners.forEach(owner -> owner.purgeCourse(course.tenantId(), course.courseId()));
        if (!courses.hardDelete(course.tenantId(), course.courseId())) {
            return false;
        }
        items.deleteAllOfCourse(course.tenantId(), course.courseId());
        modules.deleteAllOfCourse(course.tenantId(), course.courseId());
        return true;
    }
}
