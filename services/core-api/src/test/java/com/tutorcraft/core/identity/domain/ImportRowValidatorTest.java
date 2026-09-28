package com.tutorcraft.core.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ImportRowValidatorTest {

    private static final Set<String> ROLES = Set.of("teacher", "assistant", "student", "observer", "guest");

    private final ImportRowValidator validator = new ImportRowValidator(
            Set.of("existing@school.ru"), Set.of("MATH-7"), ROLES, "student");

    @Test
    void validNewUserWithCourseGetsDefaultRole() {
        ImportRow row = single(new ImportRow.Raw(2, " Anna@School.RU ", "Анна", "Петрова", "MATH-7", null));

        assertThat(row.valid()).isTrue();
        assertThat(row.email()).isEqualTo("anna@school.ru");
        assertThat(row.roleKey()).isEqualTo("student");
        assertThat(row.existingUser()).isFalse();
    }

    @Test
    void newUserWithoutCourseHasNoRole() {
        ImportRow row = single(new ImportRow.Raw(2, "a@b.ru", "A", "B", "", ""));

        assertThat(row.valid()).isTrue();
        assertThat(row.courseShortName()).isNull();
        assertThat(row.roleKey()).isNull();
    }

    @Test
    void reportsInvalidEmailAndMissingNames() {
        ImportRow row = single(new ImportRow.Raw(3, "not-an-email", " ", null, null, null));

        assertThat(row.errors()).extracting(ImportRowError::field, ImportRowError::code).containsExactly(
                org.assertj.core.groups.Tuple.tuple("email", ImportRowValidator.INVALID_EMAIL),
                org.assertj.core.groups.Tuple.tuple("firstName", ImportRowValidator.REQUIRED),
                org.assertj.core.groups.Tuple.tuple("lastName", ImportRowValidator.REQUIRED));
        assertThat(row.errors()).allMatch(error -> error.row() == 3);
    }

    @Test
    void secondOccurrenceOfEmailIsDuplicate() {
        List<ImportRow> rows = validator.validate(List.of(
                new ImportRow.Raw(2, "a@b.ru", "A", "B", null, null),
                new ImportRow.Raw(3, "A@B.ru", "A", "B", null, null)));

        assertThat(rows.get(0).valid()).isTrue();
        assertThat(rows.get(1).errors()).extracting(ImportRowError::code).containsExactly(ImportRowValidator.DUPLICATE_IN_FILE);
    }

    @Test
    void existingUserIsOnlyEnrolledAndNamesAreOptional() {
        ImportRow row = single(new ImportRow.Raw(2, "existing@school.ru", null, null, "MATH-7", "teacher"));

        assertThat(row.valid()).isTrue();
        assertThat(row.existingUser()).isTrue();
        assertThat(row.roleKey()).isEqualTo("teacher");
    }

    @Test
    void existingUserWithoutCourseHasNothingToImport() {
        ImportRow row = single(new ImportRow.Raw(2, "existing@school.ru", "X", "Y", null, null));

        assertThat(row.errors()).extracting(ImportRowError::code).containsExactly(ImportRowValidator.ALREADY_EXISTS);
    }

    @Test
    void unknownCourseAndRoleAreReported() {
        ImportRow row = single(new ImportRow.Raw(2, "a@b.ru", "A", "B", "PHYS-9", "boss"));

        assertThat(row.errors()).extracting(ImportRowError::code)
                .containsExactly(ImportRowValidator.UNKNOWN_COURSE, ImportRowValidator.INVALID_ROLE);
    }

    @Test
    void roleWithoutCourseIsReported() {
        ImportRow row = single(new ImportRow.Raw(2, "a@b.ru", "A", "B", null, "teacher"));

        assertThat(row.errors()).extracting(ImportRowError::field, ImportRowError::code)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("courseShortName", ImportRowValidator.COURSE_REQUIRED));
    }

    @Test
    void tooLongNameIsReported() {
        ImportRow row = single(new ImportRow.Raw(2, "a@b.ru", "x".repeat(ImportRowValidator.MAX_NAME_LENGTH + 1), "B", null, null));

        assertThat(row.errors()).extracting(ImportRowError::code).containsExactly(ImportRowValidator.TOO_LONG);
    }

    private ImportRow single(ImportRow.Raw raw) {
        return validator.validate(List.of(raw)).get(0);
    }
}
