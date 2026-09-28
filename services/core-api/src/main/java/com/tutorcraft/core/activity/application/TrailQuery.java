package com.tutorcraft.core.activity.application;

import java.time.Instant;
import java.util.UUID;

/** Выборка событий вокруг записи: ровно один из якорей (пользователь, вкладка браузера, IP) задан. */
public record TrailQuery(Instant from, Instant to, UUID userId, String sessionId, String ip, int limit) {
}
