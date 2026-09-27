package com.tutorcraft.core.courses;

import java.util.List;

/** Доступность элемента/модуля для студента (FR-PROG-03, UX-07). reasons — локализованный текст. */
public record Availability(boolean available, String mode, List<String> reasons) {

    public static final String SHOW_LOCKED = "show_locked";
    public static final String HIDE = "hide";

    public static Availability open() {
        return new Availability(true, SHOW_LOCKED, List.of());
    }
}
