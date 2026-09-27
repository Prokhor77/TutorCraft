package com.tutorcraft.core.files.spi;

import java.util.UUID;

/**
 * Порт проверки права чтения файла через его владельца (FileLink). Реализуют модули-владельцы:
 * courses ('item', 'course'), assessment ('submission', 'feedback', 'attempt'), communication ('post'),
 * identity ('user' — аватары видны всем в tenant). Реализация зависит только от своих репозиториев и AccessService.
 */
public interface FileOwnerAccess {

    String ownerType();

    boolean canRead(UUID tenantId, UUID userId, UUID ownerId);
}
