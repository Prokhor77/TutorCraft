package com.tutorcraft.core.identity.domain;

import java.util.List;

/**
 * Строка CSV-импорта пользователей (FR-USER-02) после проверки.
 *
 * @param row             номер строки файла (заголовок — строка 1)
 * @param existingUser    пользователь с таким email уже есть в tenant — будет только записан на курс
 * @param courseShortName краткое имя курса или null
 * @param roleKey         ключ роли в курсе (по умолчанию student) или null, если курса нет
 */
public record ImportRow(int row, String email, String firstName, String lastName, String courseShortName, String roleKey,
                        boolean existingUser, List<ImportRowError> errors) {

    public ImportRow {
        errors = List.copyOf(errors);
    }

    public boolean valid() {
        return errors.isEmpty();
    }

    public boolean hasCourse() {
        return courseShortName != null;
    }

    /** Исходные значения ячеек строки. */
    public record Raw(int row, String email, String firstName, String lastName, String courseShortName, String role) {
    }
}
