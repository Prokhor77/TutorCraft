package com.tutorcraft.core.communication.calendar.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Личные события (заметки) и iCal-токены пользователей. */
public interface CalendarRepository {

    void insertEvent(PersonalEvent event);

    Optional<PersonalEvent> findEvent(UUID tenantId, UUID userId, UUID eventId);

    void updateEvent(PersonalEvent event);

    void deleteEvent(UUID tenantId, UUID userId, UUID eventId);

    /** События, пересекающие [from, to). */
    List<PersonalEvent> events(UUID tenantId, UUID userId, Instant from, Instant to);

    /** Заменяет токен пользователя (старая ссылка перестаёт работать). */
    void replaceIcalToken(UUID tenantId, UUID userId, String tokenHash, Instant now);

    Optional<IcalOwner> findIcalOwner(String tokenHash);

    record PersonalEvent(UUID id, UUID tenantId, UUID userId, String title, String description, boolean allDay,
                         Instant startsAt, Instant endsAt, Instant createdAt, Instant updatedAt) {
    }

    record IcalOwner(UUID tenantId, UUID userId) {
    }
}
