package com.tutorcraft.core.progress.application;

import com.tutorcraft.core.progress.domain.ItemCompletion;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Выполнение элементов и курсов, последние просмотры (PostgreSQL: completion_states, course_completions, item_views). */
public interface CompletionRepository {

    Optional<ItemCompletion> find(UUID tenantId, UUID itemId, UUID userId);

    void save(ItemCompletion completion, String source, Instant at);

    Set<UUID> completedItems(UUID tenantId, UUID courseId, UUID userId);

    /** courseId → выполненные элементы пользователя. */
    Map<UUID, Set<UUID>> completedItemsByCourse(UUID tenantId, UUID userId, Collection<UUID> courseIds);

    /** userId → выполненные элементы курса. */
    Map<UUID, Set<UUID>> completedItemsByUser(UUID tenantId, UUID courseId);

    void recordView(UUID tenantId, UUID courseId, UUID itemId, UUID userId, Instant at);

    Optional<UUID> lastViewedItem(UUID tenantId, UUID courseId, UUID userId);

    Optional<Instant> courseCompletedAt(UUID tenantId, UUID courseId, UUID userId);

    /** @return false — курс уже был завершён раньше */
    boolean markCourseCompleted(UUID tenantId, UUID courseId, UUID userId, Instant at);

    /** userId → дата завершения курса. */
    Map<UUID, Instant> courseCompletions(UUID tenantId, UUID courseId);
}
