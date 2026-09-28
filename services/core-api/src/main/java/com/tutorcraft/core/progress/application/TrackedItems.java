package com.tutorcraft.core.progress.application;

import com.tutorcraft.core.courses.ItemRef;
import com.tutorcraft.core.courses.Visibility;
import com.tutorcraft.core.progress.domain.CompletionRule;
import java.util.List;

/** Элементы, входящие в процент выполнения: с отслеживанием выполнения и не скрытые от студентов. */
final class TrackedItems {

    private TrackedItems() {
    }

    static List<ItemRef> of(List<ItemRef> items) {
        return items.stream().filter(TrackedItems::counts).toList();
    }

    static boolean counts(ItemRef item) {
        return item.visibility() != Visibility.HIDDEN
                && CompletionRule.of(item.completionMode(), item.completionTriggers()).tracked();
    }
}
