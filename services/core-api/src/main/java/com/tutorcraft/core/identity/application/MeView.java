package com.tutorcraft.core.identity.application;

import java.util.List;
import java.util.UUID;

/** Профиль текущего пользователя (контракт: тип Me). */
public record MeView(UUID id, String email, String firstName, String lastName, String avatarUrl, String timezone,
                     String locale, TenantView tenant, List<String> tenantRoles, boolean telegramLinked) {

    public record TenantView(UUID id, String slug, String name, Branding branding) {
    }

    public record Branding(String logoUrl, String primaryColor) {
    }
}
