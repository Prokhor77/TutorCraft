package com.tutorcraft.core.identity.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Построчная проверка CSV-импорта (FR-USER-02). Чистая функция: внешние факты (существующие email,
 * известные курсы, допустимые роли) передаются параметрами.
 */
public final class ImportRowValidator {

    public static final String FIELD_EMAIL = "email";
    public static final String FIELD_FIRST_NAME = "firstName";
    public static final String FIELD_LAST_NAME = "lastName";
    public static final String FIELD_COURSE = "courseShortName";
    public static final String FIELD_ROLE = "role";

    public static final String REQUIRED = "required";
    public static final String INVALID_EMAIL = "invalid_email";
    public static final String DUPLICATE_IN_FILE = "duplicate_in_file";
    public static final String TOO_LONG = "too_long";
    public static final String UNKNOWN_COURSE = "unknown_course";
    public static final String INVALID_ROLE = "invalid_role";
    public static final String COURSE_REQUIRED = "course_required";
    public static final String ALREADY_EXISTS = "already_exists";

    public static final int MAX_NAME_LENGTH = 100;

    private final Set<String> existingEmails;
    private final Set<String> knownCourses;
    private final Set<String> allowedRoles;
    private final String defaultRole;

    /**
     * @param existingEmails нормализованные email пользователей tenant
     * @param knownCourses   краткие имена существующих курсов tenant
     */
    public ImportRowValidator(Set<String> existingEmails, Set<String> knownCourses, Set<String> allowedRoles, String defaultRole) {
        this.existingEmails = Set.copyOf(existingEmails);
        this.knownCourses = Set.copyOf(knownCourses);
        this.allowedRoles = Set.copyOf(allowedRoles);
        this.defaultRole = defaultRole;
    }

    public List<ImportRow> validate(List<ImportRow.Raw> rows) {
        Set<String> seen = new HashSet<>();
        List<ImportRow> result = new ArrayList<>(rows.size());
        for (ImportRow.Raw raw : rows) {
            result.add(validateRow(raw, seen));
        }
        return result;
    }

    private ImportRow validateRow(ImportRow.Raw raw, Set<String> seen) {
        List<ImportRowError> errors = new ArrayList<>();
        String email = EmailAddress.normalize(blankToNull(raw.email()));
        checkEmail(raw.row(), email, seen, errors);
        boolean existing = email != null && existingEmails.contains(email);
        if (!existing) {
            checkName(raw.row(), raw.firstName(), FIELD_FIRST_NAME, errors);
            checkName(raw.row(), raw.lastName(), FIELD_LAST_NAME, errors);
        }
        String course = blankToNull(raw.courseShortName());
        String role = blankToNull(raw.role());
        checkCourseAndRole(raw.row(), course, role, errors);
        if (existing && course == null) {
            errors.add(error(raw.row(), FIELD_EMAIL, ALREADY_EXISTS));
        }
        String roleKey = course == null ? null : (role == null ? defaultRole : role);
        return new ImportRow(raw.row(), email, trim(raw.firstName()), trim(raw.lastName()), course, roleKey, existing, errors);
    }

    private static void checkEmail(int row, String email, Set<String> seen, List<ImportRowError> errors) {
        if (email == null) {
            errors.add(error(row, FIELD_EMAIL, REQUIRED));
            return;
        }
        if (!EmailAddress.isValid(email)) {
            errors.add(error(row, FIELD_EMAIL, INVALID_EMAIL));
            return;
        }
        if (!seen.add(email)) {
            errors.add(error(row, FIELD_EMAIL, DUPLICATE_IN_FILE));
        }
    }

    private static void checkName(int row, String value, String field, List<ImportRowError> errors) {
        String name = blankToNull(value);
        if (name == null) {
            errors.add(error(row, field, REQUIRED));
            return;
        }
        if (name.length() > MAX_NAME_LENGTH) {
            errors.add(error(row, field, TOO_LONG));
        }
    }

    private void checkCourseAndRole(int row, String course, String role, List<ImportRowError> errors) {
        if (course != null && !knownCourses.contains(course)) {
            errors.add(error(row, FIELD_COURSE, UNKNOWN_COURSE));
        }
        if (role == null) {
            return;
        }
        if (!allowedRoles.contains(role)) {
            errors.add(error(row, FIELD_ROLE, INVALID_ROLE));
        } else if (course == null) {
            errors.add(error(row, FIELD_COURSE, COURSE_REQUIRED));
        }
    }

    private static ImportRowError error(int row, String field, String code) {
        return new ImportRowError(row, field, code, null);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
