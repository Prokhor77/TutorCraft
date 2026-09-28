package com.tutorcraft.core.identity.domain;

/** Ошибка строки импорта; message заполняется локализованным текстом на уровне application. */
public record ImportRowError(int row, String field, String code, String message) {

    public ImportRowError withMessage(String text) {
        return new ImportRowError(row, field, code, text);
    }
}
