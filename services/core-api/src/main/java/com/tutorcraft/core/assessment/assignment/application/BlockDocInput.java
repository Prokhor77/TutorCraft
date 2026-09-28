package com.tutorcraft.core.assessment.assignment.application;

import com.tutorcraft.core.shared.content.BlockDocs;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.persistence.JsonCodec;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Санитизация блочных документов из ввода студентов и проверяющих (белый список блоков, NFR-SEC).
 * Встраивания (embed) в ответах и отзывах не разрешены — пустой белый список доменов.
 */
@Component
class BlockDocInput {

    static final int MAX_SERIALIZED_LENGTH = 500_000;
    private static final Set<String> NO_EMBEDS = Set.of();

    private final JsonCodec json;

    BlockDocInput(JsonCodec json) {
        this.json = json;
    }

    SanitizedText sanitize(Map<String, Object> doc, String field) {
        if (doc == null) {
            return new SanitizedText(null, Set.of());
        }
        if (json.write(doc).length() > MAX_SERIALIZED_LENGTH) {
            throw ValidationException.single(field, "too_long", "Document is too large");
        }
        BlockDocs.SanitizedDoc sanitized = BlockDocs.sanitize(doc, NO_EMBEDS, field);
        return new SanitizedText(sanitized.doc(), Set.copyOf(sanitized.fileIds()));
    }

    record SanitizedText(Map<String, Object> doc, Set<UUID> fileIds) {
    }
}
