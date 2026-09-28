package com.tutorcraft.core.courses.spi;

import com.tutorcraft.core.courses.ItemType;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Контракт типа элемента (FR-EXT-01, DATA-04): схема настроек, умолчания, валидация, ключевые даты.
 * Реализации: материалы — в courses; assignment — assessment.assignment; quiz — assessment.quiz; forum — communication.forum.
 * Новый тип добавляется новой реализацией без изменения ядра.
 */
public interface ActivityType {

    ItemType type();

    /** Настройки по умолчанию (UX-02: элемент создаётся по одному названию). */
    Map<String, Object> defaults();

    /**
     * Валидирует и нормализует настройки (слияние с текущими выполняет вызывающий код).
     * @throws com.tutorcraft.core.shared.domain.ValidationException с полями вида {@code settings.<name>}
     */
    Map<String, Object> validate(Map<String, Object> settings);

    default Optional<Instant> dueAt(Map<String, Object> settings) {
        return Optional.empty();
    }

    default Optional<Instant> openAt(Map<String, Object> settings) {
        return Optional.empty();
    }

    default Optional<Instant> closeAt(Map<String, Object> settings) {
        return Optional.empty();
    }

    /** Поля настроек, скрываемые от студента (например, ключи). */
    default Map<String, Object> learnerView(Map<String, Object> settings) {
        return settings;
    }

    /**
     * Файлы, на которые ссылаются нормализованные настройки. Модуль courses проверяет их готовность
     * ({@code FilesApi.requireAllReady}) и связывает с элементом (FileLink 'item') — чтение файла разрешается правами на элемент.
     */
    default Set<UUID> referencedFileIds(Map<String, Object> settings) {
        return Set.of();
    }
}
