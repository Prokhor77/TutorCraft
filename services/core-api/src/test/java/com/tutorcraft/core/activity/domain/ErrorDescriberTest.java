package com.tutorcraft.core.activity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErrorDescriberTest {

    private static final int STACK_LIMIT = 8000;

    @Test
    void usesRootCauseTypeAndMaskedMessage() {
        IllegalStateException root = new IllegalStateException("no user petrov@mail.ru");
        RuntimeException wrapper = new RuntimeException("wrapper", root);

        ActivityEntry.ErrorDetails details = ErrorDescriber.describe("internal.error", wrapper, STACK_LIMIT);

        assertThat(details.code()).isEqualTo("internal.error");
        assertThat(details.type()).isEqualTo(IllegalStateException.class.getName());
        assertThat(details.message()).isEqualTo("no user ***");
        assertThat(details.stack()).contains("java.lang.RuntimeException").contains("Caused by: java.lang.IllegalStateException");
    }

    @Test
    void collapsesLibraryFramesAndKeepsApplicationFrames() {
        RuntimeException error = new RuntimeException("boom");
        error.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("org.lib.First", "a", "First.java", 1),
            new StackTraceElement("org.lib.Second", "b", "Second.java", 2),
            new StackTraceElement("org.lib.Third", "c", "Third.java", 3),
            new StackTraceElement("com.tutorcraft.core.Svc$$SpringCGLIB$$0", "d", null, -1),
            new StackTraceElement("com.tutorcraft.core.Svc", "e", "Svc.java", 5),
        });

        String stack = ErrorDescriber.describe("internal.error", error, STACK_LIMIT).stack();

        assertThat(stack).contains("at org.lib.First.a").doesNotContain("org.lib.Second").doesNotContain("SpringCGLIB")
                .contains("... 3 more").contains("at com.tutorcraft.core.Svc.e");
    }

    @Test
    void withoutExceptionOnlyCodeIsKept() {
        assertThat(ErrorDescriber.describe("course.not_found", null, STACK_LIMIT))
                .isEqualTo(new ActivityEntry.ErrorDetails("course.not_found", null, null, null));
    }

    @Test
    void stackIsTruncated() {
        assertThat(ErrorDescriber.describe("internal.error", new RuntimeException("x"), 20).stack()).hasSize(20);
    }
}
