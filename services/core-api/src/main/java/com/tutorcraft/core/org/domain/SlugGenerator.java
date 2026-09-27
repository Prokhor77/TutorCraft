package com.tutorcraft.core.org.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/** Транслитерация и нормализация slug (латиница, цифры, дефис). */
public final class SlugGenerator {

    private static final int MAX_LENGTH = 48;
    private static final String FALLBACK = "school";
    private static final Map<Character, String> CYRILLIC = Map.ofEntries(
            Map.entry('а', "a"), Map.entry('б', "b"), Map.entry('в', "v"), Map.entry('г', "g"), Map.entry('д', "d"),
            Map.entry('е', "e"), Map.entry('ё', "e"), Map.entry('ж', "zh"), Map.entry('з', "z"), Map.entry('и', "i"),
            Map.entry('й', "y"), Map.entry('к', "k"), Map.entry('л', "l"), Map.entry('м', "m"), Map.entry('н', "n"),
            Map.entry('о', "o"), Map.entry('п', "p"), Map.entry('р', "r"), Map.entry('с', "s"), Map.entry('т', "t"),
            Map.entry('у', "u"), Map.entry('ф', "f"), Map.entry('х', "h"), Map.entry('ц', "ts"), Map.entry('ч', "ch"),
            Map.entry('ш', "sh"), Map.entry('щ', "sch"), Map.entry('ъ', ""), Map.entry('ы', "y"), Map.entry('ь', ""),
            Map.entry('э', "e"), Map.entry('ю', "yu"), Map.entry('я', "ya"), Map.entry('і', "i"), Map.entry('ў', "u"));

    private SlugGenerator() {
    }

    public static String slugify(String input) {
        if (input == null || input.isBlank()) {
            return FALLBACK;
        }
        StringBuilder out = new StringBuilder();
        for (char c : input.toLowerCase(Locale.ROOT).toCharArray()) {
            out.append(CYRILLIC.getOrDefault(c, String.valueOf(c)));
        }
        String ascii = Normalizer.normalize(out, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = ascii.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        if (slug.isEmpty()) {
            return FALLBACK;
        }
        return slug.length() > MAX_LENGTH ? slug.substring(0, MAX_LENGTH).replaceAll("-+$", "") : slug;
    }
}
