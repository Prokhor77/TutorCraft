package com.tutorcraft.core.assessment.assignment.domain;

import com.tutorcraft.core.shared.domain.Validator;
import java.util.List;
import java.util.Locale;

/** Ограничения содержимого сдачи из настроек задания (FR-ASSIGN-02): способ сдачи, число, размер и типы файлов. */
public final class SubmissionContentRules {

    private static final String FILES_FIELD = "fileIds";
    private static final String TEXT_FIELD = "text";
    private static final char EXTENSION_SEPARATOR = '.';

    private SubmissionContentRules() {
    }

    public record SubmittedFile(String name, long sizeBytes) {
    }

    /** @throws com.tutorcraft.core.shared.domain.ValidationException при нарушениях */
    public static void validate(AssignmentSettings settings, List<SubmittedFile> files, boolean hasText) {
        Validator validator = new Validator()
                .check(files.isEmpty() || settings.submissionType().filesAllowed(), FILES_FIELD, "files_not_allowed",
                        "This assignment does not accept files")
                .check(!hasText || settings.submissionType().textAllowed(), TEXT_FIELD, "text_not_allowed",
                        "This assignment does not accept online text")
                .check(files.size() <= settings.maxFiles(), FILES_FIELD, "too_many_files",
                        "At most " + settings.maxFiles() + " files are allowed")
                .check(files.stream().allMatch(file -> file.sizeBytes() <= settings.maxFileSizeBytes()), FILES_FIELD,
                        "file_too_large", "Each file must be at most " + settings.maxFileSizeMb() + " MB")
                .check(files.stream().allMatch(file -> extensionAllowed(settings, file.name())), FILES_FIELD,
                        "extension_not_allowed", "Allowed file types: " + String.join(", ", settings.allowedExtensions()));
        validator.throwIfInvalid();
    }

    static boolean extensionAllowed(AssignmentSettings settings, String fileName) {
        if (settings.allowedExtensions().isEmpty()) {
            return true;
        }
        int dot = fileName.lastIndexOf(EXTENSION_SEPARATOR);
        if (dot < 0 || dot == fileName.length() - 1) {
            return false;
        }
        return settings.allowedExtensions().contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
