package com.tutorcraft.core.access.domain;

import java.util.UUID;

/** Назначение роли уровня tenant/категории. contextId = null для tenant/platform. */
public record RoleGrant(String roleKey, String contextType, UUID contextId) {
}
