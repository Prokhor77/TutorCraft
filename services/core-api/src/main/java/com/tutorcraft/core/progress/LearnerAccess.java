package com.tutorcraft.core.progress;

import com.tutorcraft.core.courses.ItemRef;
import java.util.UUID;

/**
 * Открыт ли элемент студенту: виден (курс, модули, элемент опубликованы) и выполнены условия доступа
 * родительских модулей и самого элемента (FR-PROG-02). Используется активностями (тест, форум) перед действием студента.
 * Персонал с правом {@code course.viewHidden} вызывающий код пропускает сам.
 */
public interface LearnerAccess {

    Status statusOf(UUID tenantId, UUID userId, ItemRef item);

    enum Status {
        OPEN, HIDDEN, LOCKED;

        public boolean open() {
            return this == OPEN;
        }
    }
}
