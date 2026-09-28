package com.tutorcraft.core.communication.forum.application;

import com.tutorcraft.core.files.FilesApi;
import com.tutorcraft.core.org.OrgApi;
import com.tutorcraft.core.shared.content.BlockDocs;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Санитизация текста поста (блочный документ) и привязка вложений к посту (владелец 'post').
 * Встраивания (iframe) — только с доменов белого списка tenant (OrgApi.embedWhitelist).
 */
@Component
public class ForumContent {

    public static final String POST_OWNER = "post";
    private static final String FIELD = "body";

    private final FilesApi files;
    private final OrgApi org;

    public ForumContent(FilesApi files, OrgApi org) {
        this.files = files;
        this.org = org;
    }

    public Map<String, Object> sanitizeAndLink(UUID tenantId, Map<String, Object> body, UUID postId) {
        BlockDocs.SanitizedDoc sanitized = BlockDocs.sanitize(body, org.embedWhitelist(tenantId), FIELD);
        if (!sanitized.fileIds().isEmpty()) {
            files.requireAllReady(tenantId, sanitized.fileIds(), FIELD);
            sanitized.fileIds().forEach(fileId -> files.link(tenantId, fileId, POST_OWNER, postId));
        }
        return sanitized.doc();
    }
}
