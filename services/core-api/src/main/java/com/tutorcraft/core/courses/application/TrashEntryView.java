package com.tutorcraft.core.courses.application;

import java.time.Instant;
import java.util.UUID;

/** TrashEntry (контракт §5): kind — course | module | item; itemType — только для item. */
public record TrashEntryView(String kind, UUID id, UUID courseId, String title, String itemType, Instant deletedAt,
                             Instant purgeAt) {

    public static final String KIND_COURSE = "course";
    public static final String KIND_MODULE = "module";
    public static final String KIND_ITEM = "item";
}
