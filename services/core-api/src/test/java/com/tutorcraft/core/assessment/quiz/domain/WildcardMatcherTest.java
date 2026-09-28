package com.tutorcraft.core.assessment.quiz.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WildcardMatcherTest {

    @Test
    void starMatchesAnySequenceIncludingEmpty() {
        assertThat(WildcardMatcher.matches("Pyth*", "Python", false)).isTrue();
        assertThat(WildcardMatcher.matches("*on", "on", false)).isTrue();
        assertThat(WildcardMatcher.matches("a*c", "abbbc", true)).isTrue();
        assertThat(WildcardMatcher.matches("a*c", "abd", true)).isFalse();
    }

    @Test
    void regexCharactersAreLiteral() {
        assertThat(WildcardMatcher.matches("1+1=2", "1+1=2", true)).isTrue();
        assertThat(WildcardMatcher.matches("a.c", "abc", true)).isFalse();
    }

    @Test
    void caseSensitivityAndWhitespaceTrimming() {
        assertThat(WildcardMatcher.matches("Москва", "  москва ", false)).isTrue();
        assertThat(WildcardMatcher.matches("Москва", "москва", true)).isFalse();
    }

    @Test
    void nullNeverMatches() {
        assertThat(WildcardMatcher.matches(null, "x", false)).isFalse();
        assertThat(WildcardMatcher.matches("x", null, false)).isFalse();
    }
}
