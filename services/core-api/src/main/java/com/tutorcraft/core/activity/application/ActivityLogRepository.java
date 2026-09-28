package com.tutorcraft.core.activity.application;

import com.tutorcraft.core.activity.application.ActivityViews.EntryView;
import com.tutorcraft.core.activity.application.ActivityViews.SummaryView;
import com.tutorcraft.core.activity.domain.ActivityEntry;
import com.tutorcraft.core.shared.api.PageQuery;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityLogRepository {

    void insertAll(List<ActivityEntry> entries);

    List<EntryView> search(ActivityScope scope, ActivityFilter filter, PageQuery page);

    /** Запись со стеком ошибки. */
    Optional<EntryView> find(ActivityScope scope, UUID id);

    /** События по возрастанию времени; не более {@code query.limit() + 1} строк (лишняя — признак усечения). */
    List<EntryView> trail(ActivityScope scope, TrailQuery query);

    SummaryView summarize(ActivityScope scope, Instant from, Instant to, int topRoutes);

    /** Удаляет не более {@code batchSize} записей старше {@code cutoff}; возвращает число удалённых. */
    int purgeBefore(Instant cutoff, int batchSize);
}
