package com.tutorcraft.core.assessment.assignment.domain;

import java.util.List;
import java.util.Map;

/** Проверки блочного документа (контракт BlockDoc) без разбора схемы — схему проверяет санитайзер. */
public final class BlockDocContent {

    private static final String BLOCKS = "blocks";

    private BlockDocContent() {
    }

    public static boolean hasText(Map<String, Object> doc) {
        return doc != null && doc.get(BLOCKS) instanceof List<?> blocks && !blocks.isEmpty();
    }
}
