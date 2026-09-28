package com.tutorcraft.core.enrollment.application;

import java.util.List;
import java.util.UUID;

/** Group (контракт §6). */
public record GroupView(UUID id, String name, List<UUID> memberIds) {
}
