package com.tutorcraft.core.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordRulesTest {

    private final PasswordRules defaults = new PasswordRules(10, true, true);

    @Test
    void acceptsPasswordMatchingPolicy() {
        assertThat(defaults.violations("correct7horse")).isEmpty();
    }

    @Test
    void reportsEveryViolatedRule() {
        assertThat(defaults.violations("short")).containsExactly(PasswordRules.TOO_SHORT, PasswordRules.DIGIT_REQUIRED);
        assertThat(defaults.violations("1234567890")).containsExactly(PasswordRules.LETTER_REQUIRED);
    }

    @Test
    void neverAllowsFewerThanAbsoluteMinimum() {
        PasswordRules lax = new PasswordRules(4, false, false);

        assertThat(lax.violations("abcdefg")).containsExactly(PasswordRules.TOO_SHORT);
        assertThat(lax.violations("abcdefgh")).isEmpty();
    }

    @Test
    void rejectsTooLongPassword() {
        assertThat(defaults.violations("a1".repeat(PasswordRules.MAX_LENGTH))).contains(PasswordRules.TOO_LONG);
    }

    @Test
    void nullPasswordIsTooShort() {
        assertThat(new PasswordRules(8, false, false).violations(null)).containsExactly(PasswordRules.TOO_SHORT);
    }

    @Test
    void cyrillicLettersCountAsLetters() {
        assertThat(defaults.violations("пароль12345")).isEmpty();
    }
}
