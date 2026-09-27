package com.tutorcraft.core.access.domain;

public enum RoleScope {
    PLATFORM("platform"), TENANT("tenant"), CATEGORY("category"), COURSE("course");

    private final String key;

    RoleScope(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
