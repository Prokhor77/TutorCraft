'use client';
import { ScrollText } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { DateTimeInput, Input } from '@/components/ui/input';
import { LoadMore } from '@/components/ui/load-more';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { SkeletonList } from '@/components/ui/skeleton';
import { Table, TableContainer, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { useAuditLog } from '@/features/admin/use-admin';
import { flattenPages } from '@/lib/api/pagination';
import { formatDateTime } from '@/lib/utils/format';
import { fromDateTimeLocalValue } from '@/lib/utils/time';

const DIFF_INDENT = 2;

/** FR-REPORT-02: audit log with filters (who / what / when / where / object). */
export default function AdminAuditPage() {
  const t = useTranslations('adminAudit');
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
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-2 md:flex-row">
        <Input
          aria-label={t('objectType')}
          placeholder={t('objectType')}
          value={objectType}
          onChange={(event) => setObjectType(event.target.value)}
          className="md:w-56"
        />
        <label className="flex items-center gap-2 text-sm">
          {t('from')}
          <DateTimeInput value={from} onChange={(event) => setFrom(event.target.value)} />
        </label>
        <label className="flex items-center gap-2 text-sm">
          {t('to')}
          <DateTimeInput value={to} onChange={(event) => setTo(event.target.value)} />
        </label>
      </div>
      {log.isLoading ? <SkeletonList label={tCommon('loading')} /> : null}
      {log.isError ? (
        <ErrorState
          title={t('loadError')}
          retryLabel={tCommon('retry')}
          onRetry={() => void log.refetch()}
        />
      ) : null}
      {log.isSuccess && rows.length === 0 ? (
        <EmptyState icon={ScrollText} title={t('emptyTitle')} description={t('emptyText')} />
      ) : null}
      {rows.length > 0 ? (
        <TableContainer>
          <Table>
            <THead>
              <tr>
                <TH>{t('at')}</TH>
                <TH>{t('actor')}</TH>
                <TH>{t('action')}</TH>
                <TH>{t('object')}</TH>
                <TH>{t('ip')}</TH>
                <TH>{t('diff')}</TH>
              </tr>
            </THead>
            <TBody>
              {rows.map((entry) => (
                <TR key={entry.id}>
                  <TD className="whitespace-nowrap text-xs">{formatDateTime(entry.at, locale)}</TD>
                  <TD>{entry.actorName ?? t('system')}</TD>
                  <TD className="font-mono text-xs">{entry.action}</TD>
                  <TD className="text-xs">
                    {entry.objectType} <span className="text-text-muted">{entry.objectId}</span>
                  </TD>
                  <TD className="text-xs text-text-muted">{entry.ip ?? '—'}</TD>
                  <TD>
                    {entry.diff ? (
                      <Popover>
                        <PopoverTrigger asChild>
                          <Button variant="ghost" size="sm">
                            {t('show')}
                          </Button>
                        </PopoverTrigger>
                        <PopoverContent className="w-96">
                          <pre className="max-h-72 overflow-auto rounded-sm bg-surface-muted p-2 text-xs">
                            {JSON.stringify(entry.diff, null, DIFF_INDENT)}
                          </pre>
                        </PopoverContent>
                      </Popover>
                    ) : (
                      '—'
                    )}
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>
        </TableContainer>
      ) : null}
      <LoadMore
        hasMore={!!log.hasNextPage}
        loading={log.isFetchingNextPage}
        onClick={() => void log.fetchNextPage()}
        label={tCommon('loadMore')}
      />
    </div>
  );
}
