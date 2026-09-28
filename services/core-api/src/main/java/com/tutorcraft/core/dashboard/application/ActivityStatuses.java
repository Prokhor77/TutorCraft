package com.tutorcraft.core.dashboard.application;

import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.courses.spi.ItemStatusProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Статусы активностей студента через ItemStatusProvider модулей (задания, тесты); без провайдера — not_started. */
@Component
class ActivityStatuses {

    static final String NOT_STARTED = "not_started";
    private static final Set<String> DONE = Set.of("submitted", "submitted_late", "graded");

    private final List<ItemStatusProvider> providers;

    ActivityStatuses(List<ItemStatusProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    Map<UUID, String> of(UUID tenantId, UUID userId, List<ItemRef> items) {
        Map<UUID, String> statuses = new HashMap<>();
        items.forEach(item -> statuses.put(item.id(), NOT_STARTED));
        providers.forEach(provider -> {
            List<ItemRef> supported = items.stream().filter(item -> provider.supportedTypes().contains(item.type())).toList();
            if (!supported.isEmpty()) {
                statuses.putAll(provider.statuses(tenantId, userId, supported));
            }
        });
        return statuses;
    }

    static boolean done(String status) {
        return DONE.contains(status);
    }
}
