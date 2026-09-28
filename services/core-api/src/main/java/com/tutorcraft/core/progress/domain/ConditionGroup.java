package com.tutorcraft.core.progress.domain;

import java.util.List;

/** Список условий с логикой «все/любое» (FR-PROG-02). {@code showWhenLocked} — показывать с замком или скрывать (FR-PROG-03). */
public record ConditionGroup(Operator operator, boolean showWhenLocked, List<Condition> conditions) {

    public enum Operator {
        ALL("all"), ANY("any");

        private final String key;

        Operator(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    public ConditionGroup {
        conditions = List.copyOf(conditions);
    }

    public boolean isEmpty() {
        return conditions.isEmpty();
    }
}
