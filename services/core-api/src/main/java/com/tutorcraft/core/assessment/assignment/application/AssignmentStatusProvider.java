package com.tutorcraft.core.assessment.assignment.application;

import com.tutorcraft.core.assessment.assignment.domain.SubmissionStatus;
import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.courses.ItemType;
import com.tutorcraft.core.courses.spi.ItemStatusProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Статус заданий студента для оглавления и «Моих задач»: статус текущей попытки или not_started. */
@Component
class AssignmentStatusProvider implements ItemStatusProvider {

    static final String NOT_STARTED = "not_started";

    private final SubmissionRepository submissions;

    AssignmentStatusProvider(SubmissionRepository submissions) {
        this.submissions = submissions;
    }

    @Override
    public Set<ItemType> supportedTypes() {
        return Set.of(ItemType.ASSIGNMENT);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> statuses(UUID tenantId, UUID userId, List<ItemRef> items) {
        List<UUID> itemIds = items.stream().filter(item -> item.type() == ItemType.ASSIGNMENT).map(ItemRef::id).toList();
        Map<UUID, SubmissionStatus> found = submissions.latestStatusesForMember(tenantId, userId, itemIds);
        Map<UUID, String> result = new HashMap<>();
        itemIds.forEach(itemId -> result.put(itemId,
                found.containsKey(itemId) ? found.get(itemId).key() : NOT_STARTED));
        return result;
    }
}
