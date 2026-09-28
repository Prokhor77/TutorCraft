package com.tutorcraft.core.assessment.assignment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tutorcraft.core.assessment.assignment.domain.SubmissionContentRules.SubmittedFile;
import com.tutorcraft.core.shared.domain.FieldViolation;
import com.tutorcraft.core.shared.domain.ValidationException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SubmissionContentRulesTest {

    private static final long MB = 1024L * 1024L;

    @Test
    void filesWithinLimitsAreAccepted() {
        AssignmentSettings settings = settings(SubmissionType.FILE, List.of("pdf"), 2, 10);
        assertThatCode(() -> SubmissionContentRules.validate(settings,
                List.of(new SubmittedFile("essay.PDF", 10 * MB), new SubmittedFile("b.pdf", 1)), false))
                .doesNotThrowAnyException();
    }

    @Test
    void limitsAndTypesAreEnforced() {
        AssignmentSettings settings = settings(SubmissionType.FILE, List.of("pdf"), 1, 1);

        assertThatThrownBy(() -> SubmissionContentRules.validate(settings,
                List.of(new SubmittedFile("a.exe", 2 * MB), new SubmittedFile("b.pdf", 1)), true))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).violations()).extracting(FieldViolation::code)
                        .containsExactlyInAnyOrder("text_not_allowed", "too_many_files", "file_too_large",
                                "extension_not_allowed"));
    }

    @Test
    void textOnlyAssignmentRejectsFiles() {
        assertThatThrownBy(() -> SubmissionContentRules.validate(settings(SubmissionType.TEXT, List.of(), 5, 10),
                List.of(new SubmittedFile("a.pdf", 1)), true))
                .satisfies(e -> assertThat(((ValidationException) e).violations()).extracting(FieldViolation::code)
                        .containsExactly("files_not_allowed"));
    }

    @Test
    void extensionMatchingHandlesMissingExtensionsAndEmptyWhitelist() {
        AssignmentSettings restricted = settings(SubmissionType.FILE, List.of("pdf"), 5, 10);
        assertThat(SubmissionContentRules.extensionAllowed(restricted, "README")).isFalse();
        assertThat(SubmissionContentRules.extensionAllowed(restricted, "file.")).isFalse();
        assertThat(SubmissionContentRules.extensionAllowed(settings(SubmissionType.FILE, List.of(), 5, 10), "any.exe")).isTrue();
    }

    private static AssignmentSettings settings(SubmissionType type, List<String> extensions, int maxFiles, int maxMb) {
        return new AssignmentSettings(type, BigDecimal.TEN, null, null, null, extensions, maxFiles, maxMb, null, false, true,
                null, true);
    }
}
