package com.tutorcraft.core.courses.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tutorcraft.core.courses.ItemType;
import com.tutorcraft.core.courses.domain.CourseItem.KeyDates;
import com.tutorcraft.core.courses.spi.ActivityType;
import com.tutorcraft.core.shared.domain.ValidationException;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** UX-02 и DATA-04: умолчания типа, слияние настроек, проверка схемой, ключевые даты. */
class ActivityTypeRegistryTest {

    private static final Instant DUE = Instant.parse("2026-10-01T18:00:00Z");

    /** Тестовый тип «задание»: maxScore > 0, dueAt — ISO-строка. */
    private static final class FakeAssignment implements ActivityType {

        @Override
        public ItemType type() {
            return ItemType.ASSIGNMENT;
        }

        @Override
        public Map<String, Object> defaults() {
            Map<String, Object> defaults = new LinkedHashMap<>();
            defaults.put("maxScore", 100);
            defaults.put("dueAt", null);
            return defaults;
        }

        @Override
        public Map<String, Object> validate(Map<String, Object> settings) {
            if (!(settings.get("maxScore") instanceof Integer score) || score <= 0) {
                throw ValidationException.single("settings.maxScore", "invalid", "maxScore must be positive");
            }
            return new LinkedHashMap<>(settings);
        }

        @Override
        public Optional<Instant> dueAt(Map<String, Object> settings) {
            return Optional.ofNullable((String) settings.get("dueAt")).map(Instant::parse);
        }
    }

    private final ActivityTypeRegistry registry = new ActivityTypeRegistry(List.of(new FakeAssignment()));

    @Test
    void initialSettingsUseDefaultsAndKind() {
        Map<String, Object> settings = registry.initialSettings(ItemType.ASSIGNMENT, null);

        assertThat(settings).containsEntry("maxScore", 100).containsEntry("kind", "assignment").containsEntry("dueAt", null);
    }

    @Test
    void patchIsMergedOverCurrentAndValidated() {
        Map<String, Object> current = registry.initialSettings(ItemType.ASSIGNMENT, null);
        Map<String, Object> patch = new HashMap<>();
        patch.put("dueAt", DUE.toString());

        Map<String, Object> merged = registry.mergedSettings(ItemType.ASSIGNMENT, current, patch);

        assertThat(merged).containsEntry("maxScore", 100).containsEntry("dueAt", DUE.toString());
        assertThat(registry.keyDates(ItemType.ASSIGNMENT, merged)).isEqualTo(new KeyDates(DUE, null, null));
        assertThatThrownBy(() -> registry.mergedSettings(ItemType.ASSIGNMENT, current, Map.of("maxScore", 0)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void clientCannotSpoofKind() {
        assertThat(registry.initialSettings(ItemType.ASSIGNMENT, Map.of("kind", "quiz"))).containsEntry("kind", "assignment");
    }

    @Test
    void unregisteredTypeIsValidationError() {
        assertThatThrownBy(() -> registry.initialSettings(ItemType.QUIZ, null))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).violations().get(0).code())
                        .isEqualTo(ActivityTypeRegistry.INVALID_TYPE_CODE));
        assertThat(registry.learnerView(ItemType.QUIZ, Map.of("secret", 1))).containsOnlyKeys("kind");
    }

    @Test
    void duplicateRegistrationFailsFast() {
        assertThatThrownBy(() -> new ActivityTypeRegistry(List.of(new FakeAssignment(), new FakeAssignment())))
                .isInstanceOf(IllegalStateException.class);
    }
}
