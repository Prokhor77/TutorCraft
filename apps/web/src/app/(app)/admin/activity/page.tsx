'use client';
import { Activity } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import {
  ActivityFilters,
  DEFAULT_ACTIVITY_FILTERS,
  type ActivityFilterState,
} from '@/components/admin/activity/activity-filters';
import { ActivitySummaryCards } from '@/components/admin/activity/activity-summary';
import { ActivityTable } from '@/components/admin/activity/activity-table';
import { TrailSheet } from '@/components/admin/activity/trail-sheet';
import { AdminTablePanel } from '@/components/admin/admin-panel';
import { Badge } from '@/components/ui/badge';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { LoadMore } from '@/components/ui/load-more';
import { SkeletonList } from '@/components/ui/skeleton';
import { useActivityLog } from '@/features/activity/use-activity';
import { useDebouncedValue } from '@/features/app/use-debounced-value';
import type { ActivityQuery } from '@/lib/api/endpoints/activity';
import { flattenPages } from '@/lib/api/pagination';
import { fromDateTimeLocalValue } from '@/lib/utils/time';
import { useAdminTenantStore } from '@/stores/admin-tenant-store';

function toQuery(filters: ActivityFilterState, allTenants: boolean): Omit<ActivityQuery, 'cursor'> {
  return {
    allTenants: allTenants || undefined,
    outcome: filters.outcome === 'all' ? undefined : filters.outcome,
    kind: filters.kind === 'all' ? undefined : filters.kind,
    actor: filters.actor.trim() || undefined,
    route: filters.route.trim() || undefined,
    requestId: filters.requestId || undefined,
    from: fromDateTimeLocalValue(filters.from) ?? undefined,
    to: fromDateTimeLocalValue(filters.to) ?? undefined,
    includeAnonymous: filters.includeAnonymous || undefined,
  };
}

/**
 * Activity log (FR-REPORT-02): every action on the platform or in the selected school — who, what, on which page, how
 * it ended — and, for any failure, the trail of what the person did before it. Complements the audit log (business
 * changes only).
 */
export default function AdminActivityPage() {
  const t = useTranslations('adminActivity');
  const tAdmin = useTranslations('admin');
  const tCommon = useTranslations('common');
  const [filters, setFilters] = useState(DEFAULT_ACTIVITY_FILTERS);
  const [selected, setSelected] = useState<string | null>(null);
  const allTenants = useAdminTenantStore((state) => state.logScope === 'all');
  const log = useActivityLog(toQuery(useDebouncedValue(filters), allTenants));
  const rows = flattenPages(log.data?.pages);
  return (
    <div className="flex flex-col gap-4 md:gap-gutter">
      <ActivitySummaryCards
        includeAnonymous={filters.includeAnonymous}
        allTenants={allTenants}
        onRouteClick={(route) => setFilters({ ...filters, route, outcome: 'errors' })}
      />
      <AdminTablePanel
        title={tAdmin('nav.activity')}
        count={
          rows.length > 0 ? (
            <Badge tone="primary">{t('shownCount', { count: rows.length })}</Badge>
          ) : null
        }
        description={t(allTenants ? 'panelHintAll' : 'panelHint')}
        toolbar={<ActivityFilters value={filters} onChange={setFilters} />}
        footer={
          <LoadMore
            hasMore={!!log.hasNextPage}
            loading={log.isFetchingNextPage}
            onClick={() => void log.fetchNextPage()}
            label={tCommon('loadMore')}
          />
        }
      >
        {log.isLoading ? (
          <div className="p-5 sm:p-6">
            <SkeletonList label={tCommon('loading')} />
          </div>
        ) : null}
        {log.isError ? (
          <div className="p-5 sm:p-6">
            <ErrorState
              title={t('loadError')}
              retryLabel={tCommon('retry')}
              onRetry={() => void log.refetch()}
            />
          </div>
        ) : null}
        {log.isSuccess && rows.length === 0 ? (
          <div className="p-5 sm:p-6">
            <EmptyState icon={Activity} title={t('emptyTitle')} description={t('emptyText')} />
          </div>
        ) : null}
        {rows.length > 0 ? (
          <ActivityTable entries={rows} onOpen={setSelected} showSchool={allTenants} />
        ) : null}
      </AdminTablePanel>
      <TrailSheet
        entryId={selected}
        allTenants={allTenants}
        onSelect={setSelected}
        onClose={() => setSelected(null)}
      />
    </div>
  );
}
