package com.tutorcraft.core.assessment.assignment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tutorcraft.core.shared.domain.FieldViolation;
import com.tutorcraft.core.shared.domain.ValidationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssignmentSettingsParserTest {

    @Test
    void emptySettingsGiveAc2Defaults() {
        AssignmentSettings settings = AssignmentSettingsParser.parse(Map.of());

        assertThat(settings.submissionType()).isEqualTo(SubmissionType.FILE);
        assertThat(settings.maxScore()).isEqualByComparingTo("100");
        assertThat(settings.dueAt()).isNull();
        assertThat(settings.maxFiles()).isEqualTo(5);
        assertThat(settings.maxFileSizeMb()).isEqualTo(50);
        assertThat(settings.requireSubmitButton()).isTrue();
        assertThat(settings.autoPublishGrades()).isTrue();
        assertThat(AssignmentSettingsParser.parse(null)).isEqualTo(settings);
    }

    @Test
    void normalizedMapRoundTrips() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("submissionType", "both");
        raw.put("maxScore", 12.5);
        raw.put("dueAt", "2026-10-01T18:00:00Z");
        raw.put("allowedExtensions", List.of(".PDF", "docx", "pdf"));
        raw.put("maxAttempts", 3);
        raw.put("gradeCategoryId", UUID.randomUUID().toString());
        raw.put("unknown", "dropped");

        Map<String, Object> normalized = AssignmentSettingsParser.parse(raw).toMap();

        assertThat(normalized).containsEntry("kind", "assignment").containsEntry("submissionType", "both")
                .containsEntry("maxScore", 12.5).containsEntry("dueAt", "2026-10-01T18:00:00Z")
                .containsEntry("allowedExtensions", List.of("pdf", "docx")).doesNotContainKey("unknown");
        assertThat(AssignmentSettingsParser.parse(normalized)).isEqualTo(AssignmentSettingsParser.parse(raw));
    }

    @Test
    void integralMaxScoreIsStoredAsInteger() {
        assertThat(AssignmentSettings.defaults().toMap()).containsEntry("maxScore", 100L);
    }

    @Test
    void acceptsNumericStringsAndInstants() {
        Instant due = Instant.parse("2026-10-01T18:00:00Z");
        AssignmentSettings settings = AssignmentSettingsParser.parse(Map.of("maxScore", "20", "dueAt", due, "maxFiles", 2L));

        assertThat(settings.maxScore()).isEqualByComparingTo(BigDecimal.valueOf(20));
        assertThat(settings.dueAt()).isEqualTo(due);
        assertThat(settings.maxFiles()).isEqualTo(2);
    }

    @Test
    void invalidValuesAreReportedPerField() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("submissionType", "video");
        raw.put("maxScore", -1);
        raw.put("maxFiles", 2.5);
        raw.put("maxFileSizeMb", 1000);
        raw.put("dueAt", "tomorrow");
        raw.put("allowedExtensions", List.of("p d f"));
        raw.put("requireSubmitButton", "yes");
        raw.put("gradeCategoryId", "not-a-uuid");

        assertThatThrownBy(() -> AssignmentSettingsParser.parse(raw))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).violations()).extracting(FieldViolation::field)
                        .contains("settings.submissionType", "settings.maxScore", "settings.maxFiles",
                                "settings.maxFileSizeMb", "settings.dueAt", "settings.allowedExtensions",
                                "settings.requireSubmitButton", "settings.gradeCategoryId"));
    }

    @Test
    void datesMustBeOrdered() {
        Map<String, Object> raw = Map.of("openAt", "2026-10-05T00:00:00Z", "dueAt", "2026-10-04T00:00:00Z",
                "closeAt", "2026-10-03T00:00:00Z");

        assertThatThrownBy(() -> AssignmentSettingsParser.parse(raw))
                .satisfies(e -> assertThat(((ValidationException) e).violations()).extracting(FieldViolation::code)
                        .contains("before_open", "before_due"));
    }
}
