package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.files.FilesApi;
import com.tutorcraft.core.shared.content.BlockDocs;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Санитизация блочных документов тестов (текст вопроса, отзыв, эссе) и привязка упомянутых в них файлов к владельцу.
 * iframe-вставки в вопросах и ответах не разрешены (пустой белый список).
 */
@Component
public class QuizContent {

    public static final String QUESTION_OWNER = "question";
    public static final String ATTEMPT_OWNER = "attempt";
    private static final Set<String> NO_EMBEDS = Set.of();

    private final FilesApi files;

    public QuizContent(FilesApi files) {
        this.files = files;
    }

    /** @return санитизированный документ; null → null */
    public Map<String, Object> sanitizeAndLink(UUID tenantId, Map<String, Object> doc, String ownerType, UUID ownerId,
                                               String field) {
        if (doc == null) {
            return null;
        }
        BlockDocs.SanitizedDoc sanitized = BlockDocs.sanitize(doc, NO_EMBEDS, field);
        linkFiles(tenantId, sanitized.fileIds(), ownerType, ownerId, field);
        return sanitized.doc();
    }

    public void linkFiles(UUID tenantId, Collection<UUID> fileIds, String ownerType, UUID ownerId, String field) {
        if (fileIds.isEmpty()) {
            return;
        }
        files.requireAllReady(tenantId, fileIds, field);
        fileIds.forEach(fileId -> files.link(tenantId, fileId, ownerType, ownerId));
    }
}
