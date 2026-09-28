package com.tutorcraft.core.courses.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** FR-COURSE-HYB-01: slug лендинга курса. */
class CourseSlugsTest {

    @ParameterizedTest
    @CsvSource({
        "'Основы Python: для начинающих!', osnovy-python-dlya-nachinayuschih",
        "'  Math 101  ', math-101",
        "'Щука и ёж', schuka-i-ezh",
        "'Café crème', cafe-creme",
        "'C++ / C#', c-c"
    })
    void transliteratesAndNormalizes(String title, String expected) {
        assertThat(CourseSlugs.slugify(title)).isEqualTo(expected);
    }

    @Test
    void fallsBackForEmptyOrSymbolOnlyTitles() {
        assertThat(CourseSlugs.slugify(null)).isEqualTo(CourseSlugs.FALLBACK);
        assertThat(CourseSlugs.slugify("   ")).isEqualTo(CourseSlugs.FALLBACK);
        assertThat(CourseSlugs.slugify("!!!")).isEqualTo(CourseSlugs.FALLBACK);
    }

    @Test
    void limitsLengthWithoutTrailingDash() {
        String slug = CourseSlugs.slugify("word ".repeat(40));
        assertThat(slug).hasSizeLessThanOrEqualTo(CourseSlugs.MAX_LENGTH).doesNotEndWith("-");
    }

    @Test
    void uniqueAppendsFirstFreeNumber() {
        assertThat(CourseSlugs.unique("math", Set.of())).isEqualTo("math");
        assertThat(CourseSlugs.unique("math", Set.of("math"))).isEqualTo("math-2");
        assertThat(CourseSlugs.unique("math", Set.of("math", "math-2", "math-3"))).isEqualTo("math-4");
    }

    @Test
    void uniqueKeepsMaxLength() {
        String base = "a".repeat(CourseSlugs.MAX_LENGTH);
        assertThat(CourseSlugs.unique(base, Set.of(base))).hasSizeLessThanOrEqualTo(CourseSlugs.MAX_LENGTH).endsWith("-2");
    }
}
