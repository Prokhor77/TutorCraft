package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.assessment.quiz.domain.QuestionDraft;
import com.tutorcraft.core.assessment.quiz.domain.QuestionVersion;
import com.tutorcraft.core.assessment.quiz.domain.StoredQuestion;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.security.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Сборка новой версии вопроса: санитизация документов, привязка файлов, номер версии. */
@Component
public class QuestionVersionFactory {

    private final QuizContent content;
    private final Clock clock;

    public QuestionVersionFactory(QuizContent content, Clock clock) {
        this.content = content;
        this.clock = clock;
    }

    /** @param current текущая «голова» вопроса; null — новый вопрос */
    public NewVersion create(CurrentUser user, UUID courseId, UUID questionId, StoredQuestion current, QuestionDraft draft) {
        Instant now = clock.instant();
        int number = current == null ? 1 : current.currentVersion() + 1;
        UUID versionId = Ids.newId();
        Map<String, Object> body = content.sanitizeAndLink(user.tenantId(), draft.body(), QuizContent.QUESTION_OWNER, questionId, "body");
        Map<String, Object> feedback = content.sanitizeAndLink(user.tenantId(), draft.generalFeedback(), QuizContent.QUESTION_OWNER, questionId,
                "generalFeedback");
        QuestionVersion version = new QuestionVersion(versionId, user.tenantId(), questionId, number, draft.type(), draft.title(),
                body, draft.data(), draft.defaultScore(), feedback, now, user.userId());
        StoredQuestion question = new StoredQuestion(questionId, user.tenantId(), courseId, draft.categoryId(), draft.tags(),
                draft.type(), draft.title(), number, versionId, current == null ? now : current.createdAt(), now);
        return new NewVersion(question, version);
    }

    public record NewVersion(StoredQuestion question, QuestionVersion version) {
    }
}
