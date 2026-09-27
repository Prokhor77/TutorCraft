package com.tutorcraft.core.courses;

import java.util.UUID;

/** Доменные события модуля courses (Spring ApplicationEvent, публикуются после коммита данных). */
public final class CourseEvents {

    private CourseEvents() {
    }

    public enum ChangeKind { CREATED, UPDATED, DELETED, RESTORED }

    public record ItemChanged(UUID tenantId, UUID courseId, UUID itemId, ItemType type, ChangeKind kind, UUID actorId) {
    }

    public record CourseChanged(UUID tenantId, UUID courseId, ChangeKind kind, UUID actorId) {
    }

    /** Студент открыл элемент (для выполнения «просмотрено», FR-PROG-01). */
    public record ItemViewed(UUID tenantId, UUID courseId, UUID itemId, UUID userId) {
    }
}
