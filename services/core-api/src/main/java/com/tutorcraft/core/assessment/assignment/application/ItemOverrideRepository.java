package com.tutorcraft.core.assessment.assignment.application;

import com.tutorcraft.core.assessment.assignment.domain.ItemOverride;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Продления сроков заданий (таблица item_overrides). */
public interface ItemOverrideRepository {

    /** Вставка или замена продления того же студента/группы. @return сохранённое продление */
    ItemOverride upsert(ItemOverride override);

    List<ItemOverride> ofItem(UUID tenantId, UUID itemId);

    /** Продления, применимые к студенту: личное и групп, в которых он состоит. */
    List<ItemOverride> applicable(UUID tenantId, UUID itemId, UUID userId, Collection<UUID> groupIds);

    Optional<ItemOverride> find(UUID tenantId, UUID overrideId);

    void delete(UUID tenantId, UUID overrideId);
}
