package com.tutorcraft.core.org.domain;

import java.util.UUID;

public record Category(UUID id, UUID tenantId, UUID parentId, String name, int position) {

    public static final int MAX_NAME_LENGTH = 200;
}
