package com.tutorcraft.core.communication.forum.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tutorcraft.core.shared.domain.ValidationException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Видимость постов (Q&A, скрытые), окно правки, настройки форума (FR-FORUM-02/04). */
class ForumRulesTest {

    private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");
    private static final UUID TEACHER = new UUID(0, 1);
    private static final UUID ALICE = new UUID(0, 2);
    private static final UUID BOB = new UUID(0, 3);
    private static final Duration WINDOW = Duration.ofMinutes(30);

    private static ForumPost post(int n, UUID parentId, UUID author, boolean hidden) {
        return new ForumPost(new UUID(9, n), new UUID(1, 1), new UUID(1, 2), new UUID(1, 3), new UUID(1, 4), parentId,
                parentId == null ? 0 : 1, author, Map.of(), hidden, NOW.plusSeconds(n), null);
    }

    private final ForumPost question = post(1, null, TEACHER, false);
    private final ForumPost aliceAnswer = post(2, question.id(), ALICE, false);
    private final ForumPost bobAnswer = post(3, question.id(), BOB, false);
    private final List<ForumPost> thread = List.of(question, aliceAnswer, bobAnswer);

    @Test
    void qaStudentSeesOthersOnlyAfterOwnAnswer() {
        UUID carol = new UUID(0, 4);
        assertThat(PostVisibility.visible(thread, ForumType.QA, carol, false)).containsExactly(question);
        assertThat(PostVisibility.visible(thread, ForumType.QA, ALICE, false)).containsExactlyElementsOf(thread);
        assertThat(PostVisibility.visible(thread, ForumType.QA, carol, true)).containsExactlyElementsOf(thread);
        assertThat(PostVisibility.visible(thread, ForumType.GENERAL, carol, false)).containsExactlyElementsOf(thread);
    }

    @Test
    void hiddenPostsAndTheirBranchesAreForModeratorsOnly() {
        ForumPost hidden = post(4, question.id(), BOB, true);
        ForumPost replyToHidden = post(5, hidden.id(), ALICE, false);
        List<ForumPost> posts = List.of(question, hidden, replyToHidden);
        assertThat(PostVisibility.visible(posts, ForumType.GENERAL, ALICE, false)).containsExactly(question);
        assertThat(PostVisibility.visible(posts, ForumType.GENERAL, TEACHER, true)).containsExactlyElementsOf(posts);
    }

    @Test
    void qaHiddenOwnPostDoesNotUnlockOthers() {
        ForumPost hiddenOwn = post(4, question.id(), ALICE, true);
        List<ForumPost> posts = List.of(question, bobAnswer, hiddenOwn);
        assertThat(PostVisibility.visible(posts, ForumType.QA, ALICE, false)).containsExactly(question);
    }

    @Test
    void authorEditsWithinWindowModeratorAlways() {
        assertThat(PostPolicy.canEdit(aliceAnswer, ALICE, false, aliceAnswer.createdAt().plus(Duration.ofMinutes(29)), WINDOW))
                .isTrue();
        assertThat(PostPolicy.canEdit(aliceAnswer, ALICE, false, aliceAnswer.createdAt().plus(WINDOW), WINDOW)).isFalse();
        assertThat(PostPolicy.canEdit(aliceAnswer, BOB, false, NOW, WINDOW)).isFalse();
        assertThat(PostPolicy.canEdit(aliceAnswer, TEACHER, true, NOW.plus(Duration.ofDays(9)), WINDOW)).isTrue();
    }

    @Test
    void authorDeletesOnlyWithinWindowAndWithoutReplies() {
        Instant soon = aliceAnswer.createdAt().plusSeconds(60);
        assertThat(PostPolicy.canDelete(aliceAnswer, ALICE, false, soon, WINDOW, false)).isTrue();
        assertThat(PostPolicy.canDelete(aliceAnswer, ALICE, false, soon, WINDOW, true)).isFalse();
        assertThat(PostPolicy.canDelete(aliceAnswer, TEACHER, true, soon, WINDOW, true)).isTrue();
    }

    @Test
    void settingsDefaultsAndValidation() {
        ForumSettings defaults = ForumSettings.parse(null);
        assertThat(defaults).isEqualTo(ForumSettings.defaults());
        assertThat(defaults.editWindow()).isEqualTo(WINDOW);
        ForumSettings qa = ForumSettings.parse(Map.of("forumType", "qa", "editWindowMinutes", 0));
        assertThat(ForumSettings.parse(qa.toMap())).isEqualTo(qa);
        assertThatThrownBy(() -> ForumSettings.parse(Map.of("forumType", "chat"))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> ForumSettings.parse(Map.of("editWindowMinutes", -1))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> ForumSettings.parse(Map.of("gradeCategoryId", "x"))).isInstanceOf(ValidationException.class);
    }
}
