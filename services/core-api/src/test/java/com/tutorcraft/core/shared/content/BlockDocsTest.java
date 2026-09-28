package com.tutorcraft.core.shared.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.tutorcraft.core.shared.content.BlockDocs.SanitizedDoc;
import com.tutorcraft.core.shared.domain.FieldViolation;
import com.tutorcraft.core.shared.domain.ValidationException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** FR-CONTENT-01, NFR-SEC-03, NFR-A11Y-01, DATA-05: схема, белые списки, лимиты и файлы блочного документа. */
class BlockDocsTest {

    private static final Set<String> WHITELIST = Set.of("www.youtube.com", "player.vimeo.com");
    private static final UUID FILE = UUID.fromString("0192f3c1-0000-7000-8000-000000000001");

    private static Map<String, Object> doc(Object... blocks) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("schemaVersion", 1);
        doc.put("blocks", List.of(blocks));
        return doc;
    }

    private static Map<String, Object> block(String id, String type, Object... keyValues) {
        Map<String, Object> block = new HashMap<>();
        block.put("id", id);
        block.put("type", type);
        for (int i = 0; i < keyValues.length; i += 2) {
            block.put((String) keyValues[i], keyValues[i + 1]);
        }
        return block;
    }

    private static List<Object> text(String value) {
        return List.of(Map.of("text", value));
    }

    private static List<FieldViolation> violations(Object doc) {
        try {
            BlockDocs.sanitize(doc, WHITELIST, "content");
            throw new AssertionError("Expected ValidationException");
        } catch (ValidationException e) {
            return e.violations();
        }
    }

    private static void assertViolation(Object doc, String field, String code) {
        assertThat(violations(doc)).extracting(FieldViolation::field, FieldViolation::code).contains(tuple(field, code));
    }

    @Nested
    class DocumentShape {

        @Test
        void acceptsEmptyDocumentAndNormalizesSchemaVersion() {
            SanitizedDoc result = BlockDocs.sanitize(doc(), WHITELIST);

            assertThat(result.doc()).containsEntry("schemaVersion", 1).containsEntry("blocks", List.of());
            assertThat(result.fileIds()).isEmpty();
        }

        @Test
        void rejectsNullDocument() {
            assertViolation(null, "content", "required");
        }

        @Test
        void rejectsNonObjectDocument() {
            assertViolation("<p>html</p>", "content", "invalid");
        }

        @Test
        void rejectsUnsupportedSchemaVersion() {
            Map<String, Object> doc = doc();
            doc.put("schemaVersion", 2);
            assertViolation(doc, "content.schemaVersion", "invalid_schema_version");
        }

        @Test
        void rejectsMissingBlocks() {
            assertViolation(Map.of("schemaVersion", 1), "content.blocks", "required");
        }

        @Test
        void rejectsTooManyBlocks() {
            List<Object> blocks = new ArrayList<>();
            for (int i = 0; i <= BlockLimits.MAX_BLOCKS; i++) {
                blocks.add(block("b" + i, "paragraph", "text", text("x")));
            }
            assertViolation(Map.of("schemaVersion", 1, "blocks", blocks), "content.blocks", "too_many_blocks");
        }

        @Test
        void usesDocAsRootFieldWithoutPrefix() {
            assertThatThrownBy(() -> BlockDocs.sanitize(null, WHITELIST))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(e -> assertThat(((ValidationException) e).violations().get(0).field()).isEqualTo("doc"));
        }

        @Test
        void acceptsJsonNodeInput() throws Exception {
            JsonNode node = new ObjectMapper().readTree("""
                    {"schemaVersion":1,"blocks":[{"id":"a","type":"paragraph","text":[{"text":"Привет"}]}]}
                    """);

            SanitizedDoc result = BlockDocs.sanitize(node, WHITELIST);

            assertThat((List<?>) result.doc().get("blocks")).hasSize(1);
        }

        @Test
        void optionalReturnsEmptyForNullAndJsonNull() {
            assertThat(BlockDocs.sanitizeOptional(null, WHITELIST, "description")).isEmpty();
            assertThat(BlockDocs.sanitizeOptional(NullNode.getInstance(), WHITELIST, "description")).isEmpty();
        }
    }

    @Nested
    class Blocks {

        @Test
        void rejectsUnknownBlockType() {
            assertViolation(doc(block("a", "script", "code", "alert(1)")), "content.blocks[0].type", "invalid_block_type");
        }

        @Test
        void rejectsDuplicateAndMissingBlockIds() {
            List<FieldViolation> violations = violations(doc(block("a", "paragraph", "text", text("1")),
                    block("a", "paragraph", "text", text("2")), block(null, "paragraph", "text", text("3"))));

            assertThat(violations).extracting(FieldViolation::code).contains("duplicate_id", "required");
        }

        @Test
        void stripsUnknownFields() {
            Map<String, Object> paragraph = block("a", "paragraph", "text", text("hi"), "onclick", "evil()", "html", "<b>");

            Map<?, ?> sanitized = (Map<?, ?>) ((List<?>) BlockDocs.sanitize(doc(paragraph), WHITELIST).doc().get("blocks")).get(0);

            Set<Object> keys = Set.copyOf(sanitized.keySet());
            assertThat(keys).containsExactlyInAnyOrder("id", "type", "text");
        }

        @Test
        void acceptsAllWhitelistedBlockTypes() {
            SanitizedDoc result = BlockDocs.sanitize(doc(
                    block("h", "heading", "level", 2, "text", text("Title")),
                    block("p", "paragraph", "text", text("Body")),
                    block("l", "list", "ordered", true, "items", List.of(text("one"), text("two"))),
                    block("q", "quote", "text", text("Quote")),
                    block("c", "code", "language", "java", "code", "class A {}"),
                    block("m", "math", "latex", "e^{i\\pi}+1=0"),
                    block("t", "table", "rows", List.of(List.of(text("a"), text("b")))),
                    block("i", "image", "fileId", FILE.toString(), "alt", "Схема", "caption", "Рис. 1"),
                    block("f", "file", "fileId", FILE.toString(), "name", "notes.pdf"),
                    block("v", "video", "embedUrl", "https://www.youtube.com/embed/xyz"),
                    block("e", "embed", "url", "https://player.vimeo.com/video/1"),
                    block("n", "callout", "tone", "warning", "text", text("Careful"))), WHITELIST);

            assertThat((List<?>) result.doc().get("blocks")).hasSize(12);
            assertThat(result.fileIds()).containsExactly(FILE);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 4})
        void rejectsHeadingLevelOutOfRange(int level) {
            assertViolation(doc(block("h", "heading", "level", level, "text", text("x"))), "content.blocks[0].level", "invalid");
        }

        @Test
        void rejectsUnknownCalloutTone() {
            assertViolation(doc(block("n", "callout", "tone", "danger", "text", text("x"))), "content.blocks[0].tone", "invalid");
        }

        @Test
        void rejectsInvalidCodeLanguage() {
            assertViolation(doc(block("c", "code", "language", "<script>", "code", "x")), "content.blocks[0].language", "invalid");
        }

        @Test
        void rejectsOversizedCode() {
            String huge = "x".repeat(BlockLimits.MAX_CODE + 1);
            assertViolation(doc(block("c", "code", "language", "js", "code", huge)), "content.blocks[0].code", "too_long");
        }

        @Test
        void rejectsTableWithTooManyColumns() {
            List<Object> row = new ArrayList<>();
            for (int i = 0; i <= BlockLimits.MAX_TABLE_COLUMNS; i++) {
                row.add(text("c"));
            }
            assertViolation(doc(block("t", "table", "rows", List.of(row))), "content.blocks[0].rows[0]", "invalid");
        }
    }

    @Nested
    class RichText {

        @Test
        void keepsWhitelistedMarksAndDeduplicates() {
            Map<String, Object> span = Map.of("text", "bold", "marks", List.of("bold", "italic", "bold"));

            Map<?, ?> block = (Map<?, ?>) ((List<?>) BlockDocs.sanitize(doc(block("p", "paragraph", "text", List.of(span))),
                    WHITELIST).doc().get("blocks")).get(0);

            Map<?, ?> sanitizedSpan = (Map<?, ?>) ((List<?>) block.get("text")).get(0);
            assertThat(sanitizedSpan.get("marks")).isEqualTo(List.of("bold", "italic"));
        }

        @Test
        void rejectsUnknownMark() {
            Map<String, Object> span = Map.of("text", "x", "marks", List.of("blink"));
            assertViolation(doc(block("p", "paragraph", "text", List.of(span))), "content.blocks[0].text[0].marks", "invalid_mark");
        }

        @ParameterizedTest
        @ValueSource(strings = {"javascript:alert(1)", "JaVaScRiPt:alert(1)", "data:text/html;base64,PHNjcmlwdD4=",
            "vbscript:msgbox", "/relative/path", "//evil.example.com", "https://exa mple.com", "ftp://files.example.com"})
        void rejectsUnsafeLinks(String href) {
            Map<String, Object> span = Map.of("text", "link", "href", href);
            assertViolation(doc(block("p", "paragraph", "text", List.of(span))), "content.blocks[0].text[0].href", "invalid_href");
        }

        @ParameterizedTest
        @ValueSource(strings = {"https://example.com/page?x=1", "http://example.com", "mailto:teacher@example.com"})
        void acceptsSafeLinks(String href) {
            Map<String, Object> span = Map.of("text", "link", "href", href);
            assertThat(BlockDocs.sanitize(doc(block("p", "paragraph", "text", List.of(span))), WHITELIST).doc()).isNotNull();
        }

        @Test
        void rejectsSpanTextOverLimit() {
            Map<String, Object> span = Map.of("text", "x".repeat(BlockLimits.MAX_SPAN_TEXT + 1));
            assertViolation(doc(block("p", "paragraph", "text", List.of(span))), "content.blocks[0].text[0].text", "too_long");
        }

        @Test
        void rejectsNonArrayRichText() {
            assertViolation(doc(block("p", "paragraph", "text", "plain string")), "content.blocks[0].text", "invalid");
        }

        @Test
        void rejectsDocumentOverTotalTextLimit() {
            List<Object> blocks = new ArrayList<>();
            int perBlock = BlockLimits.MAX_SPAN_TEXT;
            long needed = BlockLimits.MAX_TOTAL_TEXT / perBlock + 1;
            for (int i = 0; i < needed; i++) {
                blocks.add(block("b" + i, "paragraph", "text", text("y".repeat(perBlock))));
            }
            assertThat(violations(Map.of("schemaVersion", 1, "blocks", blocks)))
                    .extracting(FieldViolation::code).contains("document_too_large");
        }
    }

    @Nested
    class MediaAndFiles {

        @Test
        void imageRequiresAltText() {
            assertViolation(doc(block("i", "image", "fileId", FILE.toString(), "alt", "  ")), "content.blocks[0].alt", "alt_required");
            assertViolation(doc(block("i", "image", "fileId", FILE.toString())), "content.blocks[0].alt", "alt_required");
        }

        @Test
        void fileIdMustBeUuid() {
            assertViolation(doc(block("f", "file", "fileId", "../../etc/passwd", "name", "x")), "content.blocks[0].fileId",
                    "invalid_uuid");
            assertViolation(doc(block("f", "file", "name", "x")), "content.blocks[0].fileId", "required");
        }

        @Test
        void fileBlockRequiresName() {
            assertViolation(doc(block("f", "file", "fileId", FILE.toString())), "content.blocks[0].name", "required");
        }

        @Test
        void collectsAllReferencedFiles() {
            UUID second = UUID.fromString("0192f3c1-0000-7000-8000-000000000002");
            SanitizedDoc result = BlockDocs.sanitize(doc(
                    block("i", "image", "fileId", FILE.toString(), "alt", "a"),
                    block("v", "video", "fileId", second.toString()),
                    block("f", "file", "fileId", FILE.toString(), "name", "again.pdf")), WHITELIST);

            assertThat(result.fileIds()).containsExactlyInAnyOrder(FILE, second);
        }

        @Test
        void videoNeedsExactlyOneSource() {
            assertViolation(doc(block("v", "video")), "content.blocks[0]", "file_or_embed_required");
            assertViolation(doc(block("v", "video", "fileId", FILE.toString(), "embedUrl", "https://www.youtube.com/embed/1")),
                    "content.blocks[0]", "file_or_embed_required");
        }

        @ParameterizedTest
        @ValueSource(strings = {"https://evil.example.com/embed", "https://youtube.com.evil.io/x", "javascript:alert(1)",
            "https://www.youtube.com.evil.io/embed"})
        void embedOutsideWhitelistIsRejected(String url) {
            assertViolation(doc(block("e", "embed", "url", url)), "content.blocks[0].url", "embed_not_allowed");
            assertViolation(doc(block("v", "video", "embedUrl", url)), "content.blocks[0].embedUrl", "embed_not_allowed");
        }

        @Test
        void embedHostMatchIsCaseInsensitive() {
            SanitizedDoc result = BlockDocs.sanitize(doc(block("e", "embed", "url", "https://WWW.YouTube.com/embed/1")), WHITELIST);
            assertThat(result.doc()).isNotNull();
        }

        @Test
        void emptyWhitelistRejectsEveryEmbed() {
            assertThatThrownBy(() -> BlockDocs.sanitize(doc(block("e", "embed", "url", "https://www.youtube.com/embed/1")),
                    Set.of(), "content")).isInstanceOf(ValidationException.class);
        }

        @Test
        void referencedFileIdsReadsStoredDocumentLeniently() {
            Map<String, Object> stored = Map.of("schemaVersion", 1, "blocks", List.of(
                    Map.of("id", "i", "type", "image", "fileId", FILE.toString(), "alt", "a"),
                    Map.of("id", "x", "type", "file", "fileId", "broken")));

            assertThat(BlockDocs.referencedFileIds(stored)).containsExactly(FILE);
            assertThat(BlockDocs.referencedFileIds(null)).isEmpty();
        }
    }

    @Test
    void reportsFieldPathsWithPrefix() {
        List<FieldViolation> violations = violations(doc(block("p", "paragraph", "text", text("ok")),
                block("h", "heading", "level", 9, "text", text("x"))));

        assertThat(violations).extracting(FieldViolation::field).containsExactly("content.blocks[1].level");
    }
}
