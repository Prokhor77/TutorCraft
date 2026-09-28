'use client';
import { Cpu, Filter, ScrollText } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { useState } from 'react';
import { AdminTablePanel, PersonCell } from '@/components/admin/admin-panel';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { DateTimeInput, Input } from '@/components/ui/input';
import { LoadMore } from '@/components/ui/load-more';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { SkeletonList } from '@/components/ui/skeleton';
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { useAuditLog } from '@/features/admin/use-admin';
import { flattenPages } from '@/lib/api/pagination';
import { formatDateTime } from '@/lib/utils/format';
import { fromDateTimeLocalValue } from '@/lib/utils/time';

const DIFF_INDENT = 2;

/** FR-REPORT-02: audit log with filters (who / what / when / where / object). */
export default function AdminAuditPage() {
  const t = useTranslations('adminAudit');
  const tAdmin = useTranslations('admin');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const [objectType, setObjectType] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const log = useAuditLog({
    objectType: objectType || undefined,
    from: fromDateTimeLocalValue(from) ?? undefined,
    to: fromDateTimeLocalValue(to) ?? undefined,
  });
  const rows = flattenPages(log.data?.pages);
  return (
    <AdminTablePanel
      title={tAdmin('nav.audit')}
      count={
        rows.length > 0 ? (
          <Badge tone="primary">{t('shownCount', { count: rows.length })}</Badge>
        ) : null
      }
      description={t('panelHint')}
      toolbar={
        <div className="flex flex-col gap-3 md:flex-row md:flex-wrap md:items-end">
          <div className="relative md:w-64">
            <Filter
              className="pointer-events-none absolute left-4 top-1/2 size-4 -translate-y-1/2 text-text-muted"
              aria-hidden
            />
            <Input
              aria-label={t('objectType')}
              placeholder={t('objectType')}
              value={objectType}
              onChange={(event) => setObjectType(event.target.value)}
              className="rounded-full border-transparent bg-surface-muted pl-10"
            />
          </div>
          <label className="flex items-center gap-2 text-label-md uppercase text-text-muted">
            {t('from')}
            <DateTimeInput
              value={from}
              onChange={(event) => setFrom(event.target.value)}
              className="normal-case"
            />
          </label>
          <label className="flex items-center gap-2 text-label-md uppercase text-text-muted">
            {t('to')}
            <DateTimeInput
              value={to}
              onChange={(event) => setTo(event.target.value)}
              className="normal-case"
            />
          </label>
        </div>
      }
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
          <EmptyState icon={ScrollText} title={t('emptyTitle')} description={t('emptyText')} />
        </div>
      ) : null}
      {rows.length > 0 ? (
        <Table>
          <THead>
            <tr>
              <TH>{t('actor')}</TH>
              <TH>{t('action')}</TH>
              <TH>{t('object')}</TH>
              <TH className="hidden md:table-cell">{t('at')}</TH>
              <TH className="hidden lg:table-cell">{t('ip')}</TH>
              <TH>{t('diff')}</TH>
            </tr>
          </THead>
          <TBody>
            {rows.map((entry) => (
              <TR key={entry.id}>
                <TD className="min-w-48">
                  {entry.actorName ? (
                    <PersonCell
                      name={entry.actorName}
                      secondary={
                        <span className="md:hidden">{formatDateTime(entry.at, locale)}</span>
                      }
                    />
                  ) : (
                    <span className="flex items-center gap-3">
                      <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-surface-container text-text-muted">
                        <Cpu className="size-4" aria-hidden />
                      </span>
                      <span className="flex flex-col">
                        <span className="font-semibold">{t('system')}</span>
                        <span className="text-xs text-text-muted md:hidden">
                          {formatDateTime(entry.at, locale)}
                        </span>
                      </span>
                    </span>
                  )}
                </TD>
                <TD>
                  <span className="whitespace-nowrap rounded-full bg-primary-soft px-2.5 py-1 font-mono text-xs text-primary">
                    {entry.action}
                  </span>
                </TD>
                <TD className="text-xs">
                  <span className="flex flex-col">
                    <span className="font-semibold">{entry.objectType}</span>
                    <span className="max-w-48 truncate text-text-muted">{entry.objectId}</span>
                  </span>
                </TD>
                <TD className="hidden whitespace-nowrap text-xs text-text-muted md:table-cell">
                  {formatDateTime(entry.at, locale)}
                </TD>
                <TD className="hidden font-mono text-xs text-text-muted lg:table-cell">
                  {entry.ip ?? '—'}
                </TD>
                <TD>
                  {entry.diff ? (
                    <Popover>
                      <PopoverTrigger asChild>
                        <Button variant="secondary" size="sm">
                          {t('show')}
                        </Button>
                      </PopoverTrigger>
                      <PopoverContent className="w-96 max-w-[calc(100vw-2rem)]">
                        <pre className="max-h-72 overflow-auto rounded bg-surface-muted p-3 text-xs">
                          {JSON.stringify(entry.diff, null, DIFF_INDENT)}
                        </pre>
                      </PopoverContent>
                    </Popover>
                  ) : (
                    <span className="text-text-muted">—</span>
                  )}
                </TD>
              </TR>
            ))}
          </TBody>
        </Table>
      ) : null}
    </AdminTablePanel>
  );
}
