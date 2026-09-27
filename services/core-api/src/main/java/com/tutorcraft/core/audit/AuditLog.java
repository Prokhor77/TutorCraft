package com.tutorcraft.core.audit;

/** Публичный API модуля audit. Запись выполняется в транзакции вызывающего кода. */
public interface AuditLog {

    void record(AuditRecord record);
}
