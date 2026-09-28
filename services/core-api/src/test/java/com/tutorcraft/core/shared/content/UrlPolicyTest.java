package com.tutorcraft.core.shared.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** NFR-SEC-03: схемы ссылок и белый список встраиваний. */
class UrlPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com", "http://example.com/a?b=c#d", "HTTPS://Example.com"})
    void webUrlsAreAccepted(String url) {
        assertThat(UrlPolicy.isWebUrl(url)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "example.com", "mailto:a@b.c", "javascript:alert(1)", "https://", "https://a b.com",
        "https://example.com/\u0000"})
    void nonWebUrlsAreRejected(String url) {
        assertThat(UrlPolicy.isWebUrl(url)).isFalse();
    }

    @Test
    void nullIsNeverAccepted() {
        assertThat(UrlPolicy.isWebUrl(null)).isFalse();
        assertThat(UrlPolicy.isSafeHref(null)).isFalse();
        assertThat(UrlPolicy.isAllowedEmbed(null, Set.of("example.com"))).isFalse();
    }

    @Test
    void mailtoIsSafeHrefButNotWebUrl() {
        assertThat(UrlPolicy.isSafeHref("mailto:teacher@example.com")).isTrue();
        assertThat(UrlPolicy.isWebUrl("mailto:teacher@example.com")).isFalse();
    }

    @Test
    void overlongUrlIsRejected() {
        String url = "https://example.com/" + "a".repeat(UrlPolicy.MAX_URL_LENGTH);
        assertThat(UrlPolicy.isWebUrl(url)).isFalse();
    }

    @Test
    void embedRequiresExactWhitelistedHost() {
        Set<String> whitelist = Set.of(" www.YouTube.com ", "rutube.ru");

        assertThat(UrlPolicy.isAllowedEmbed("https://www.youtube.com/embed/1", whitelist)).isTrue();
        assertThat(UrlPolicy.isAllowedEmbed("https://rutube.ru/play/embed/1", whitelist)).isTrue();
        assertThat(UrlPolicy.isAllowedEmbed("https://youtube.com/embed/1", whitelist)).isFalse();
        assertThat(UrlPolicy.isAllowedEmbed("https://sub.rutube.ru/x", whitelist)).isFalse();
        assertThat(UrlPolicy.isAllowedEmbed("https://rutube.ru.evil.io/x", whitelist)).isFalse();
        assertThat(UrlPolicy.isAllowedEmbed("https://www.youtube.com/embed/1", Set.of())).isFalse();
        assertThat(UrlPolicy.isAllowedEmbed("https://www.youtube.com/embed/1", null)).isFalse();
    }
}
