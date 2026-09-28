package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.assessment.quiz.domain.QuizSettings;
import com.tutorcraft.core.courses.ItemType;
import com.tutorcraft.core.courses.spi.ActivityType;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Тип элемента «Тест» (FR-EXT-01, DATA-04): схема и умолчания QuizSettings; срок = дата закрытия. */
@Component
class QuizActivityType implements ActivityType {

    @Override
    public ItemType type() {
        return ItemType.QUIZ;
    }

    @Override
    public Map<String, Object> defaults() {
        return QuizSettings.defaults().toMap();
    }

    @Override
    public Map<String, Object> validate(Map<String, Object> settings) {
        return QuizSettings.parse(settings).toMap();
    }

    @Override
    public Optional<Instant> dueAt(Map<String, Object> settings) {
        return closeAt(settings);
    }

    @Override
    public Optional<Instant> openAt(Map<String, Object> settings) {
        return Optional.ofNullable(QuizSettings.parse(settings).openAt());
    }

    @Override
    public Optional<Instant> closeAt(Map<String, Object> settings) {
        return Optional.ofNullable(QuizSettings.parse(settings).closeAt());
    }
}
