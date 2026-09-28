package com.tutorcraft.core.communication.notifications.domain;

import java.util.Arrays;
import java.util.Optional;

/** Каналы доставки (контракт NotificationChannel). web — центр уведомлений и push по WebSocket. */
public enum NotificationChannel {
    WEB("web"), EMAIL("email"), TELEGRAM("telegram");

    private final String key;

    NotificationChannel(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public boolean external() {
        return this != WEB;
    }

    public static Optional<NotificationChannel> find(String key) {
        return Arrays.stream(values()).filter(channel -> channel.key.equals(key)).findFirst();
    }
}
