package com.tutorcraft.core.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** FR-NOTIF-HYB-01: имя бота из env приводится к виду, который понимает t.me и Login Widget. */
class AppPropertiesOAuthTest {

    private static final Duration MAX_AGE = Duration.ofDays(1);

    private static AppProperties.OAuth withBot(String username) {
        return new AppProperties.OAuth(null, "token", username, MAX_AGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TutorCraft_bot", "@TutorCraft_bot", " @TutorCraft_bot ", "t.me/TutorCraft_bot",
            "https://t.me/TutorCraft_bot", "https://t.me/@TutorCraft_bot", "HTTPS://T.ME/TutorCraft_bot/"})
    void stripsAtSignAndLinkPrefixes(String raw) {
        assertThat(withBot(raw).telegramBotUsername()).isEqualTo("TutorCraft_bot");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {""})
    void keepsMissingUsernameMissing(String raw) {
        assertThat(withBot(raw).telegramBotUsername()).isNullOrEmpty();
    }
}
