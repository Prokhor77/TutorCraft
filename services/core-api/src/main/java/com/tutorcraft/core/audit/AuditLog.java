package com.tutorcraft.core.audit;

/** Публичный API модуля audit. */
public interface AuditLog {

    /** Запись в транзакции вызывающего кода: откатывается вместе с изменением, которое она описывает. */
    void record(AuditRecord record);

    /**
     * Запись в отдельной транзакции (REQUIRES_NEW): сохраняется, даже если вызывающий код затем бросает исключение
     * или работает в read-only транзакции. Для событий безопасности, которые сопровождают отказ (AC-1: попытка
     * доступа к объекту чужого tenant → 404 + запись аудита).
     */
    void recordIndependently(AuditRecord record);
}
