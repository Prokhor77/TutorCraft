package com.tutorcraft.core.shared.security;

import jakarta.servlet.Filter;

/**
 * Фильтр аутентификации по personal access token (реализует модуль integrations, FR-INTEG-01).
 * Встраивается в цепочку Spring Security перед JWT-фильтром; токены, которые он поддерживает,
 * не передаются JWT-декодеру.
 */
public interface PersonalAccessTokenFilter extends Filter {

    /** @return true, если bearer-токен — PAT этого фильтра (а не access JWT) */
    boolean supports(String bearerToken);
}
