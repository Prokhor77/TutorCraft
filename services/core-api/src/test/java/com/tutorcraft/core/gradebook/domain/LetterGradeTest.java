package com.tutorcraft.core.gradebook.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class LetterGradeTest {

    private static final List<ScaleLevel> FIVE_POINT = List.of(level("неудовлетворительно", 0), level("отлично", 85),
            level("удовлетворительно", 50), level("хорошо", 70));

    @Test
    void picksHighestLevelNotAbovePercent() {
        assertThat(LetterGrade.label(FIVE_POINT, new BigDecimal("84.99"))).contains("хорошо");
        assertThat(LetterGrade.label(FIVE_POINT, new BigDecimal("85"))).contains("отлично");
        assertThat(LetterGrade.label(FIVE_POINT, new BigDecimal("100"))).contains("отлично");
        assertThat(LetterGrade.label(FIVE_POINT, BigDecimal.ZERO)).contains("неудовлетворительно");
    }

    @Test
    void percentBelowAllLevelsOrMissingGivesNoLabel() {
        List<ScaleLevel> passFail = List.of(level("зачёт", 60));
        assertThat(LetterGrade.label(passFail, new BigDecimal("59.9"))).isEmpty();
        assertThat(LetterGrade.label(passFail, null)).isEmpty();
        assertThat(LetterGrade.label(null, BigDecimal.TEN)).isEmpty();
        assertThat(LetterGrade.label(List.of(), BigDecimal.TEN)).isEmpty();
    }

    private static ScaleLevel level(String name, int minPercent) {
        return new ScaleLevel(name, BigDecimal.valueOf(minPercent));
    }
}
