package com.tutorcraft.core.shared.content;

import com.tutorcraft.core.shared.content.BlockDocs.SanitizedDoc;
import com.tutorcraft.core.shared.domain.FieldViolation;
import com.tutorcraft.core.shared.domain.ValidationException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Однократный проход санитизации одного документа: накапливает нарушения, файлы и счётчик текста. */
final class BlockDocSanitizer {

    static final String FILE_ID = "fileId";

    private static final String ID = "id";
    private static final String TYPE = "type";
    private static final String TEXT = "text";
    private static final String EMBED_URL = "embedUrl";
    private static final Set<String> BLOCK_TYPES = Set.of("heading", "paragraph", "list", "quote", "code", "math",
            "table", "image", "file", "video", "embed", "callout");
    private static final Set<String> CALLOUT_TONES = Set.of("info", "warning", "success");
    private static final int MIN_HEADING_LEVEL = 1;
    private static final int MAX_HEADING_LEVEL = 3;

    private final Set<String> embedWhitelist;
    private final String prefix;
    private final SanitizerContext context = new SanitizerContext();
    private final RichTextSanitizer richText;
    private final Set<UUID> fileIds = new LinkedHashSet<>();
    private final Set<String> blockIds = new HashSet<>();

    BlockDocSanitizer(Set<String> embedWhitelist, String fieldPrefix) {
        this.embedWhitelist = embedWhitelist == null ? Set.of() : Set.copyOf(embedWhitelist);
        this.prefix = fieldPrefix == null ? "" : fieldPrefix;
        this.richText = new RichTextSanitizer(context);
    }

    SanitizedDoc sanitize(Object doc) {
        List<Object> blocks = new ArrayList<>();
        if (doc instanceof Map<?, ?> map) {
            checkSchemaVersion(map.get(BlockDocs.SCHEMA_VERSION_FIELD));
            blocks = blocks(map.get(BlockDocs.BLOCKS_FIELD));
        } else {
            context.violation(root(), doc == null ? "required" : "invalid", "Document must be an object");
        }
        context.throwIfInvalid();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(BlockDocs.SCHEMA_VERSION_FIELD, BlockDocs.SCHEMA_VERSION);
        result.put(BlockDocs.BLOCKS_FIELD, blocks);
        return new SanitizedDoc(result, Set.copyOf(fileIds));
    }

    private void checkSchemaVersion(Object version) {
        boolean valid = version instanceof Number number && number.doubleValue() == BlockDocs.SCHEMA_VERSION;
        context.check(valid, field(BlockDocs.SCHEMA_VERSION_FIELD), "invalid_schema_version", "Unsupported document schema version");
    }

    private List<Object> blocks(Object raw) {
        List<Object> result = new ArrayList<>();
        String path = field(BlockDocs.BLOCKS_FIELD);
        if (!(raw instanceof List<?> list)) {
            context.violation(path, "required", "Blocks must be an array");
            return result;
        }
        if (list.size() > BlockLimits.MAX_BLOCKS) {
            context.violation(path, "too_many_blocks", "Maximum number of blocks is " + BlockLimits.MAX_BLOCKS);
            return result;
        }
        for (int i = 0; i < list.size(); i++) {
            Map<String, Object> block = block(list.get(i), path + "[" + i + "]");
            if (block != null) {
                result.add(block);
            }
        }
        return result;
    }

    private Map<String, Object> block(Object raw, String path) {
        if (!(raw instanceof Map<?, ?> map)) {
            context.violation(path, "invalid", "Block must be an object");
            return null;
        }
        String type = map.get(TYPE) instanceof String value ? value : null;
        if (type == null || !BLOCK_TYPES.contains(type)) {
            context.violation(path + "." + TYPE, "invalid_block_type", "Unsupported block type");
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(ID, blockId(map.get(ID), path + "." + ID));
        out.put(TYPE, type);
        fillBlock(type, map, path, out);
        return out;
    }

    private void fillBlock(String type, Map<?, ?> in, String path, Map<String, Object> out) {
        switch (type) {
            case "heading" -> heading(in, path, out);
            case "paragraph", "quote" -> out.put(TEXT, richText.sanitize(in.get(TEXT), path + "." + TEXT));
            case "list" -> list(in, path, out);
            case "code" -> code(in, path, out);
            case "math" -> out.put("latex", context.string(in.get("latex"), path + ".latex", BlockLimits.MAX_LATEX, true));
            case "table" -> table(in, path, out);
            case "image" -> image(in, path, out);
            case "file" -> file(in, path, out);
            case "video" -> video(in, path, out);
            case "embed" -> embed(in, path, out);
            case "callout" -> callout(in, path, out);
            default -> throw new IllegalStateException("Unhandled block type " + type);
        }
    }

    private String blockId(Object raw, String path) {
        String id = context.string(raw, path, BlockLimits.MAX_BLOCK_ID, true);
        if (id != null && !id.isBlank() && !blockIds.add(id)) {
            context.violation(path, "duplicate_id", "Block id must be unique");
        }
        return id;
    }

    private void heading(Map<?, ?> in, String path, Map<String, Object> out) {
        Object level = in.get("level");
        boolean valid = level instanceof Number number && number.doubleValue() == number.intValue()
                && number.intValue() >= MIN_HEADING_LEVEL && number.intValue() <= MAX_HEADING_LEVEL;
        context.check(valid, path + ".level", "invalid", "Heading level must be 1, 2 or 3");
        out.put("level", valid ? ((Number) level).intValue() : MIN_HEADING_LEVEL);
        out.put(TEXT, richText.sanitize(in.get(TEXT), path + "." + TEXT));
    }

    private void list(Map<?, ?> in, String path, Map<String, Object> out) {
        out.put("ordered", Boolean.TRUE.equals(in.get("ordered")));
        List<Object> items = new ArrayList<>();
        Object raw = in.get("items");
        String itemsPath = path + ".items";
        if (!(raw instanceof List<?> list) || list.size() > BlockLimits.MAX_LIST_ITEMS) {
            context.violation(itemsPath, "invalid", "List items must be an array of at most " + BlockLimits.MAX_LIST_ITEMS);
        } else {
            for (int i = 0; i < list.size(); i++) {
                items.add(richText.sanitize(list.get(i), itemsPath + "[" + i + "]"));
            }
        }
        out.put("items", items);
    }

    private void code(Map<?, ?> in, String path, Map<String, Object> out) {
        String language = context.string(in.get("language"), path + ".language", BlockLimits.MAX_LANGUAGE, false);
        boolean validLanguage = language == null || BlockLimits.LANGUAGE_PATTERN.matcher(language).matches();
        context.check(validLanguage, path + ".language", "invalid", "Unsupported language identifier");
        out.put("language", language == null ? "" : language);
        out.put("code", context.string(in.get("code"), path + ".code", BlockLimits.MAX_CODE, true));
    }

    private void table(Map<?, ?> in, String path, Map<String, Object> out) {
        List<Object> rows = new ArrayList<>();
        String rowsPath = path + ".rows";
        if (!(in.get("rows") instanceof List<?> list) || list.size() > BlockLimits.MAX_TABLE_ROWS) {
            context.violation(rowsPath, "invalid", "Table rows must be an array of at most " + BlockLimits.MAX_TABLE_ROWS);
            out.put("rows", rows);
            return;
        }
        for (int r = 0; r < list.size(); r++) {
            rows.add(tableRow(list.get(r), rowsPath + "[" + r + "]"));
        }
        out.put("rows", rows);
    }

    private List<Object> tableRow(Object raw, String path) {
        List<Object> cells = new ArrayList<>();
        if (!(raw instanceof List<?> list) || list.size() > BlockLimits.MAX_TABLE_COLUMNS) {
            context.violation(path, "invalid", "Table row must be an array of at most " + BlockLimits.MAX_TABLE_COLUMNS);
            return cells;
        }
        for (int c = 0; c < list.size(); c++) {
            cells.add(richText.sanitize(list.get(c), path + "[" + c + "]"));
        }
        return cells;
    }

    private void image(Map<?, ?> in, String path, Map<String, Object> out) {
        out.put(FILE_ID, fileId(in.get(FILE_ID), path + "." + FILE_ID, true));
        String alt = context.string(in.get("alt"), path + ".alt", BlockLimits.MAX_ALT, false);
        context.check(alt != null && !alt.isBlank(), path + ".alt", "alt_required", "Image alternative text is required");
        out.put("alt", alt == null ? "" : alt.trim());
        String caption = context.string(in.get("caption"), path + ".caption", BlockLimits.MAX_CAPTION, false);
        if (caption != null) {
            out.put("caption", caption);
        }
    }

    private void file(Map<?, ?> in, String path, Map<String, Object> out) {
        out.put(FILE_ID, fileId(in.get(FILE_ID), path + "." + FILE_ID, true));
        String name = context.string(in.get("name"), path + ".name", BlockLimits.MAX_FILE_NAME, false);
        context.check(name != null && !name.isBlank(), path + ".name", "required", "File name is required");
        out.put("name", name == null ? "" : name.trim());
    }

    private void video(Map<?, ?> in, String path, Map<String, Object> out) {
        boolean hasFile = in.get(FILE_ID) != null;
        boolean hasEmbed = in.get(EMBED_URL) != null;
        if (hasFile == hasEmbed) {
            context.violation(path, "file_or_embed_required", "Exactly one of fileId or embedUrl is required");
            return;
        }
        if (hasFile) {
            out.put(FILE_ID, fileId(in.get(FILE_ID), path + "." + FILE_ID, true));
            return;
        }
        out.put(EMBED_URL, embedUrl(in.get(EMBED_URL), path + "." + EMBED_URL));
    }

    private void embed(Map<?, ?> in, String path, Map<String, Object> out) {
        out.put("url", embedUrl(in.get("url"), path + ".url"));
    }

    private void callout(Map<?, ?> in, String path, Map<String, Object> out) {
        Object tone = in.get("tone");
        boolean valid = tone instanceof String value && CALLOUT_TONES.contains(value);
        context.check(valid, path + ".tone", "invalid", "Callout tone must be info, warning or success");
        out.put("tone", valid ? tone : "info");
        out.put(TEXT, richText.sanitize(in.get(TEXT), path + "." + TEXT));
    }

    private String fileId(Object raw, String path, boolean required) {
        if (raw == null && !required) {
            return null;
        }
        String value = raw instanceof String text ? text : null;
        UUID id = value == null ? null : BlockDocs.parseUuid(value).orElse(null);
        if (id == null) {
            context.violation(path, raw == null ? "required" : "invalid_uuid", "A valid file identifier is required");
            return null;
        }
        fileIds.add(id);
        return id.toString();
    }

    private String embedUrl(Object raw, String path) {
        String url = context.string(raw, path, UrlPolicy.MAX_URL_LENGTH, true);
        if (url == null || url.isBlank()) {
            return url;
        }
        String trimmed = url.trim();
        context.check(UrlPolicy.isAllowedEmbed(trimmed, embedWhitelist), path, "embed_not_allowed",
                "Embedding is allowed only from whitelisted domains");
        return trimmed;
    }

    private String field(String name) {
        return prefix.isEmpty() ? name : prefix + "." + name;
    }

    private String root() {
        return prefix.isEmpty() ? "doc" : prefix;
    }

    /** Общие проверки строк и накопление нарушений (разделяется с {@link RichTextSanitizer}). */
    static final class SanitizerContext {

        private final List<FieldViolation> violations = new ArrayList<>();
        private long totalTextLength;

        void violation(String field, String code, String message) {
            if (violations.size() < BlockLimits.MAX_REPORTED_VIOLATIONS) {
                violations.add(new FieldViolation(field, code, message));
            }
        }

        void check(boolean condition, String field, String code, String message) {
            if (!condition) {
                violation(field, code, message);
            }
        }

        /** Строка не длиннее max; required — null недопустим (пустая строка допустима). */
        String string(Object raw, String path, int max, boolean required) {
            if (raw == null) {
                check(!required, path, "required", "Field is required");
                return null;
            }
            if (!(raw instanceof String value)) {
                violation(path, "invalid", "Field must be a string");
                return null;
            }
            if (value.length() > max) {
                violation(path, "too_long", "Maximum length is " + max);
                return null;
            }
            countText(value.length(), path);
            return value;
        }

        void countText(int length, String path) {
            totalTextLength += length;
            if (totalTextLength > BlockLimits.MAX_TOTAL_TEXT && totalTextLength - length <= BlockLimits.MAX_TOTAL_TEXT) {
                violation(path, "document_too_large", "Document text exceeds " + BlockLimits.MAX_TOTAL_TEXT + " characters");
            }
        }

        void throwIfInvalid() {
            if (!violations.isEmpty()) {
                throw new ValidationException(violations);
            }
        }
    }
}
