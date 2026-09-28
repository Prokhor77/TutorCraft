package com.tutorcraft.core.activity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClientPageTest {

    @Test
    void dropsQueryAndFragment() {
        assertThat(ClientPage.page("/reset-password?token=abc#top")).contains("/reset-password");
    }

    @Test
    void rejectsNonPathsAndOversizedValues() {
        assertThat(ClientPage.page("https://evil.example/x")).isEmpty();
        assertThat(ClientPage.page("/a b")).isEmpty();
        assertThat(ClientPage.page("/" + "a".repeat(400))).isEmpty();
        assertThat(ClientPage.page(" ")).isEmpty();
    }

    @Test
    void validatesSessionAndRequestIds() {
        assertThat(ClientPage.sessionId("tab-12345678")).contains("tab-12345678");
        assertThat(ClientPage.sessionId("short")).isEmpty();
        assertThat(ClientPage.requestId("<script>alert(1)</script>")).isEmpty();
    }
}
