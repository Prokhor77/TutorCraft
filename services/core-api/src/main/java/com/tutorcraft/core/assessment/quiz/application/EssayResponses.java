package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.assessment.quiz.domain.QuestionResponse;
import com.tutorcraft.core.files.FilesApi;
import com.tutorcraft.core.files.FilesApi.FileRef;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.security.CurrentUser;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Подготовка ответа к хранению: эссе санитизируется, файлы (только загруженные самим студентом)
 * привязываются к попытке (владелец 'attempt'). Остальные типы сохраняются как есть.
 */
@Component
public class EssayResponses {

    private static final String FIELD = "response.fileIds";

    private final AttemptRepository attempts;
    private final QuizContent content;
    private final FilesApi files;

    public EssayResponses(AttemptRepository attempts, QuizContent content, FilesApi files) {
        this.attempts = attempts;
        this.content = content;
        this.files = files;
    }

    public Map<String, Object> prepare(CurrentUser user, UUID attemptId, QuestionResponse response) {
        if (!(response instanceof QuestionResponse.EssayText essay)) {
            return response.toMap();
        }
        attempts.find(user.tenantId(), attemptId).filter(attempt -> attempt.ownedBy(user.userId()))
                .orElseThrow(AttemptService::notFound);
        Map<String, Object> doc = content.sanitizeAndLink(user.tenantId(), essay.essay(), QuizContent.ATTEMPT_OWNER, attemptId,
                "response.essay");
        linkOwnFiles(user, attemptId, essay.fileIds());
        return new QuestionResponse.EssayText(doc, essay.fileIds()).toMap();
    }

    private void linkOwnFiles(CurrentUser user, UUID attemptId, List<UUID> fileIds) {
        if (fileIds.isEmpty()) {
            return;
        }
        Set<UUID> unique = new HashSet<>(fileIds);
        List<FileRef> ready = files.requireAllReady(user.tenantId(), unique, FIELD);
        if (ready.stream().anyMatch(file -> !user.userId().equals(file.uploadedBy()))) {
            throw ValidationException.single(FIELD, "not_owner", "Only your own files can be attached");
        }
        unique.forEach(fileId -> files.link(user.tenantId(), fileId, QuizContent.ATTEMPT_OWNER, attemptId));
    }
}
