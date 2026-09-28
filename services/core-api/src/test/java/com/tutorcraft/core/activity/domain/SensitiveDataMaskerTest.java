package com.tutorcraft.core.activity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SensitiveDataMaskerTest {

    @Test
    void masksEmails() {
        assertThat(SensitiveDataMasker.mask("duplicate key: Key (email)=(ivan.petrov@mail.ru) already exists"))
                .isEqualTo("duplicate key: Key (email)=(***) already exists");
    }

    @Test
    void masksBearerAndJwt() {
        assertThat(SensitiveDataMasker.mask("Authorization: Bearer abc.def-ghi")).isEqualTo("Authorization: Bearer ***");
        assertThat(SensitiveDataMasker.mask("token eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.sig")).isEqualTo("token ***");
    }

    @Test
    void keepsKeyNameButHidesSecretValues() {
        assertThat(SensitiveDataMasker.mask("login failed password=Qwerty123 for user"))
                .isEqualTo("login failed password=*** for user");
        assertThat(SensitiveDataMasker.mask("api_key: s3cr3t")).isEqualTo("api_key: ***");
    }

    @Test
    void masksLongOpaqueTokensButKeepsUuids() {
        String uuid = "01a0e898-99ce-7252-b8f6-18910177e41a";
        assertThat(SensitiveDataMasker.mask("course " + uuid + " not found")).isEqualTo("course " + uuid + " not found");
        assertThat(SensitiveDataMasker.mask("link " + "a".repeat(48))).isEqualTo("link ***");
    }

    @Test
    void handlesNullAndTruncates() {
        assertThat(SensitiveDataMasker.mask(null)).isNull();
        assertThat(SensitiveDataMasker.maskAndTruncate("abcdef", 3)).isEqualTo("abc");
    }
}
