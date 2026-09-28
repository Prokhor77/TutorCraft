package com.tutorcraft.core.shared.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.tutorcraft.core.shared.domain.ValidationException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Блочные документы редактора (FR-CONTENT-01, DATA-05, NFR-SEC-03): проверка схемы и санитизация на сервере.
 * <p>
 * Результат содержит только известные блоки/поля (неизвестные поля отбрасываются), ссылки — только
 * http/https/mailto, встраивания — только домены из белого списка tenant, у изображений обязателен alt
 * (NFR-A11Y-01). {@link SanitizedDoc#fileIds()} — файлы, на которые ссылается документ: вызывающий модуль
 * проверяет их готовность ({@code FilesApi.requireAllReady}) и связывает с владельцем ({@code FilesApi.link}).
 * <p>
 * Ошибки — {@link ValidationException} с путями полей вида {@code <prefix>.blocks[3].text[0].href}.
 */
public final class BlockDocs {

    public static final int SCHEMA_VERSION = 1;
    public static final String SCHEMA_VERSION_FIELD = "schemaVersion";
    public static final String BLOCKS_FIELD = "blocks";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BlockDocs() {
    }

    /** Санитизированный документ и идентификаторы упомянутых в нём файлов. */
    public record SanitizedDoc(Map<String, Object> doc, Set<UUID> fileIds) {
    }

    public static SanitizedDoc sanitize(Object doc, Set<String> embedWhitelist) {
        return sanitize(doc, embedWhitelist, "");
    }

    /**
     * @param doc            документ как {@code Map} (JSON-объект) или {@link JsonNode}; null — ошибка {@code required}
     * @param embedWhitelist разрешённые хосты для embed/video.embedUrl (tenants.embed_whitelist)
     * @param fieldPrefix    префикс путей полей в ошибках (например {@code content}); пустая строка — без префикса
     */
    public static SanitizedDoc sanitize(Object doc, Set<String> embedWhitelist, String fieldPrefix) {
        Object normalized = normalize(doc);
        return new BlockDocSanitizer(embedWhitelist, fieldPrefix).sanitize(normalized);
    }

    /** null (или JSON null) → пусто; иначе как {@link #sanitize(Object, Set, String)}. */
    public static Optional<SanitizedDoc> sanitizeOptional(Object doc, Set<String> embedWhitelist, String fieldPrefix) {
        Object normalized = normalize(doc);
        if (normalized == null) {
            return Optional.empty();
        }
        return Optional.of(new BlockDocSanitizer(embedWhitelist, fieldPrefix).sanitize(normalized));
    }

    /**
     * Файлы, на которые ссылается уже сохранённый документ (без повторной проверки — например, при дублировании).
     * Некорректные идентификаторы пропускаются.
     */
    public static Set<UUID> referencedFileIds(Map<String, Object> doc) {
        Set<UUID> result = new LinkedHashSet<>();
        if (doc == null || !(doc.get(BLOCKS_FIELD) instanceof List<?> blocks)) {
            return result;
        }
        for (Object block : blocks) {
            if (block instanceof Map<?, ?> map && map.get(BlockDocSanitizer.FILE_ID) instanceof String raw) {
                parseUuid(raw).ifPresent(result::add);
            }
        }
        return result;
    }

    static Optional<UUID> parseUuid(String raw) {
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Object normalize(Object doc) {
        if (doc == null || doc instanceof NullNode) {
            return null;
        }
        if (doc instanceof JsonNode node) {
            return MAPPER.convertValue(node, Object.class);
        }
        return doc;
    }
}
