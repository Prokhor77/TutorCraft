package com.tutorcraft.core.assessment.assignment.application;

import com.tutorcraft.core.assessment.assignment.domain.AssignmentSettings;
import com.tutorcraft.core.assessment.assignment.domain.AssignmentSettingsParser;
import com.tutorcraft.core.courses.ItemType;
import com.tutorcraft.core.courses.spi.ActivityType;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Тип элемента «задание» (FR-EXT-01, DATA-04): умолчания AC-2, валидация и ключевые даты. */
@Component
class AssignmentActivityType implements ActivityType {

    @Override
    public ItemType type() {
        return ItemType.ASSIGNMENT;
    }

    @Override
    public Map<String, Object> defaults() {
        return AssignmentSettings.defaults().toMap();
    }

    @Override
    public Map<String, Object> validate(Map<String, Object> settings) {
        return AssignmentSettingsParser.parse(settings).toMap();
    }

    @Override
    public Optional<Instant> dueAt(Map<String, Object> settings) {
        return Optional.ofNullable(AssignmentSettingsParser.parse(settings).dueAt());
    }

    @Override
    public Optional<Instant> openAt(Map<String, Object> settings) {
        return Optional.ofNullable(AssignmentSettingsParser.parse(settings).openAt());
    }

    @Override
    public Optional<Instant> closeAt(Map<String, Object> settings) {
        return Optional.ofNullable(AssignmentSettingsParser.parse(settings).closeAt());
    }
}
