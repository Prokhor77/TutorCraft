package com.tutorcraft.core.shared.content;

import java.util.regex.Pattern;

/** Лимиты блочного документа (защита от чрезмерно больших документов, NFR-SEC-03). */
public final class BlockLimits {

    public static final int MAX_BLOCKS = 2000;
    public static final int MAX_BLOCK_ID = 64;
    public static final int MAX_SPANS = 1000;
    public static final int MAX_SPAN_TEXT = 20_000;
    public static final long MAX_TOTAL_TEXT = 1_000_000;
    public static final int MAX_LIST_ITEMS = 1000;
    public static final int MAX_TABLE_ROWS = 500;
    public static final int MAX_TABLE_COLUMNS = 50;
    public static final int MAX_CODE = 100_000;
    public static final int MAX_LANGUAGE = 32;
    public static final int MAX_LATEX = 10_000;
    public static final int MAX_ALT = 1000;
    public static final int MAX_CAPTION = 2000;
    public static final int MAX_FILE_NAME = 255;
    public static final int MAX_REPORTED_VIOLATIONS = 100;

    static final Pattern LANGUAGE_PATTERN = Pattern.compile("[A-Za-z0-9+#._-]*");

    private BlockLimits() {
    }
}
