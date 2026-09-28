package com.tutorcraft.core.courses.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Slug курса для публичного лендинга (FR-COURSE-HYB-01): транслитерация кириллицы, латиница/цифры/дефис, уникальность в tenant. */
public final class CourseSlugs {

    public static final int MAX_LENGTH = 64;
    static final String FALLBACK = "course";
    private static final String SEPARATOR = "-";
    private static final int FIRST_SUFFIX = 2;
    private static final Map<Character, String> CYRILLIC = Map.ofEntries(
            Map.entry('а', "a"), Map.entry('б', "b"), Map.entry('в', "v"), Map.entry('г', "g"), Map.entry('д', "d"),
            Map.entry('е', "e"), Map.entry('ё', "e"), Map.entry('ж', "zh"), Map.entry('з', "z"), Map.entry('и', "i"),
            Map.entry('й', "y"), Map.entry('к', "k"), Map.entry('л', "l"), Map.entry('м', "m"), Map.entry('н', "n"),
            Map.entry('о', "o"), Map.entry('п', "p"), Map.entry('р', "r"), Map.entry('с', "s"), Map.entry('т', "t"),
            Map.entry('у', "u"), Map.entry('ф', "f"), Map.entry('х', "h"), Map.entry('ц', "ts"), Map.entry('ч', "ch"),
            Map.entry('ш', "sh"), Map.entry('щ', "sch"), Map.entry('ъ', ""), Map.entry('ы', "y"), Map.entry('ь', ""),
            Map.entry('э', "e"), Map.entry('ю', "yu"), Map.entry('я', "ya"), Map.entry('і', "i"), Map.entry('ї', "yi"),
            Map.entry('є', "ye"), Map.entry('ў', "u"));

    private CourseSlugs() {
    }

    public static String slugify(String title) {
        if (title == null || title.isBlank()) {
            return FALLBACK;
        }
        StringBuilder transliterated = new StringBuilder();
        for (char c : title.toLowerCase(Locale.ROOT).toCharArray()) {
            transliterated.append(CYRILLIC.getOrDefault(c, String.valueOf(c)));
        }
        String ascii = Normalizer.normalize(transliterated, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = ascii.replaceAll("[^a-z0-9]+", SEPARATOR).replaceAll("(^-+|-+$)", "");
        if (slug.isEmpty()) {
            return FALLBACK;
        }
        return trim(slug, MAX_LENGTH);
    }

    /** Первый свободный вариант: base, base-2, base-3, … (taken — занятые slug с этим префиксом). */
    public static String unique(String base, Set<String> taken) {
        if (!taken.contains(base)) {
            return base;
        }
        for (int n = FIRST_SUFFIX; ; n++) {
            String suffix = SEPARATOR + n;
            String candidate = trim(base, MAX_LENGTH - suffix.length()) + suffix;
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
    }

    private static String trim(String slug, int max) {
        return slug.length() > max ? slug.substring(0, max).replaceAll("-+$", "") : slug;
    }
}
