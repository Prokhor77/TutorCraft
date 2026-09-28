package com.tutorcraft.core.shared.content;

import com.tutorcraft.core.shared.content.BlockDocSanitizer.SanitizerContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** RichText = массив фрагментов {text, marks?, href?}: белый список марок, безопасные ссылки. */
final class RichTextSanitizer {

    private static final String TEXT = "text";
    private static final String MARKS = "marks";
    private static final String HREF = "href";
    private static final Set<String> MARK_TYPES = Set.of("bold", "italic", "code", "strike", "underline");

    private final SanitizerContext context;

    RichTextSanitizer(SanitizerContext context) {
        this.context = context;
    }

    List<Object> sanitize(Object raw, String path) {
        List<Object> spans = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            context.violation(path, "invalid", "Rich text must be an array of spans");
            return spans;
        }
        if (list.size() > BlockLimits.MAX_SPANS) {
            context.violation(path, "too_many_spans", "Maximum number of text spans is " + BlockLimits.MAX_SPANS);
            return spans;
        }
        for (int i = 0; i < list.size(); i++) {
            Map<String, Object> span = span(list.get(i), path + "[" + i + "]");
            if (span != null) {
                spans.add(span);
            }
        }
        return spans;
    }

    private Map<String, Object> span(Object raw, String path) {
        if (!(raw instanceof Map<?, ?> map)) {
            context.violation(path, "invalid", "Text span must be an object");
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        String text = context.string(map.get(TEXT), path + "." + TEXT, BlockLimits.MAX_SPAN_TEXT, true);
        out.put(TEXT, text == null ? "" : text);
        List<String> marks = marks(map.get(MARKS), path + "." + MARKS);
        if (!marks.isEmpty()) {
            out.put(MARKS, marks);
        }
        String href = href(map.get(HREF), path + "." + HREF);
        if (href != null) {
            out.put(HREF, href);
        }
        return out;
    }

    private List<String> marks(Object raw, String path) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            context.violation(path, "invalid", "Marks must be an array");
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (Object mark : list) {
            if (mark instanceof String value && MARK_TYPES.contains(value)) {
                result.add(value);
            } else {
                context.violation(path, "invalid_mark", "Unsupported text mark");
            }
        }
        return List.copyOf(result);
    }

    private String href(Object raw, String path) {
        String href = context.string(raw, path, UrlPolicy.MAX_URL_LENGTH, false);
        if (href == null) {
            return null;
        }
        String trimmed = href.trim();
        if (!UrlPolicy.isSafeHref(trimmed)) {
            context.violation(path, "invalid_href", "Only http, https and mailto links are allowed");
            return null;
        }
        return trimmed;
    }
}
