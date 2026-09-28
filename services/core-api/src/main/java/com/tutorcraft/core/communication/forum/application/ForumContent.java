package com.tutorcraft.core.communication.forum.application;

import com.tutorcraft.core.files.FilesApi;
import com.tutorcraft.core.shared.content.BlockDocs;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Санитизация текста поста (блочный документ) и привязка вложений к посту (владелец 'post').
 * Встраивания iframe в форумах не разрешены (белый список tenant недоступен модулю через публичный API).
 */
@Component
public class ForumContent {

    public static final String POST_OWNER = "post";
    private static final Set<String> NO_EMBEDS = Set.of();
    private static final String FIELD = "body";

    private final FilesApi files;

    public ForumContent(FilesApi files) {
        this.files = files;
    }

    public Map<String, Object> sanitizeAndLink(UUID tenantId, Map<String, Object> body, UUID postId) {
        BlockDocs.SanitizedDoc sanitized = BlockDocs.sanitize(body, NO_EMBEDS, FIELD);
        if (!sanitized.fileIds().isEmpty()) {
            files.requireAllReady(tenantId, sanitized.fileIds(), FIELD);
            sanitized.fileIds().forEach(fileId -> files.link(tenantId, fileId, POST_OWNER, postId));
        }
        return sanitized.doc();
    }
}
