package com.tutorcraft.core.identity.application;

/** Сессия + профиль для ответа AuthResponse. */
public record AuthResult(IssuedSession session, MeView me) {
}
