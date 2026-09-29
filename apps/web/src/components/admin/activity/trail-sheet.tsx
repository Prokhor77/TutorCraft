'use client';
import { Copy } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import type { ReactNode } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Sheet, SheetContent } from '@/components/ui/dialog';
import { ErrorState } from '@/components/ui/error-state';
import { SkeletonList } from '@/components/ui/skeleton';
import { toast } from '@/components/ui/toast';
import { entryObjects, entryTarget, entryTone } from '@/features/activity/describe-entry';
import { useActivityTrail } from '@/features/activity/use-activity';
import type { ActivityEntry, ActivityTrail } from '@/lib/api/schemas/activity';
import { cn } from '@/lib/utils/cn';
import { copyToClipboard } from '@/lib/utils/clipboard';
import { formatDateTimePrecise, formatTime, formatTimePrecise } from '@/lib/utils/format';
import { KindIcon, OutcomeBadge } from './entry-parts';

/**
 * Trail of one record: full details (request id, handler, masked error and stack) and what the same person — or the
 * same browser tab / IP for anonymous visitors — did shortly before and after. Clicking a step re-centres the trail.
 */
export function TrailSheet({
  entryId,
  onSelect,
  onClose,
}: {
  entryId: string | null;
  onSelect: (entryId: string) => void;
  onClose: () => void;
}) {
  const t = useTranslations('adminActivity.trail');
  const tCommon = useTranslations('common');
  const trail = useActivityTrail(entryId);
  return (
    <Sheet open={entryId !== null} onOpenChange={(open) => !open && onClose()}>
      <SheetContent
        title={t('title')}
        description={t('description')}
        closeLabel={tCommon('close')}
        className="md:w-[44rem]"
      >
        {trail.isLoading ? <SkeletonList label={tCommon('loading')} /> : null}
        {trail.isError ? (
          <ErrorState
            title={t('loadError')}
            retryLabel={tCommon('retry')}
            onRetry={() => void trail.refetch()}
          />
        ) : null}
        {trail.data ? <TrailBody trail={trail.data} onSelect={onSelect} /> : null}
      </SheetContent>
    </Sheet>
  );
}

function TrailBody({
  trail,
  onSelect,
}: {
  trail: ActivityTrail;
  onSelect: (entryId: string) => void;
}) {
  const t = useTranslations('adminActivity.trail');
  const locale = useLocale();
  return (
    <div className="flex flex-col gap-5">
      <EntryDetails entry={trail.focus} />
      <section className="flex flex-col gap-2">
        <h3 className="text-label-md uppercase text-text-muted">
          {t(`anchor.${trail.anchor}`, {
            from: formatTime(trail.from, locale),
            to: formatTime(trail.to, locale),
          })}
        </h3>
        <ol className="flex flex-col border-l-2 border-border">
          {trail.events.map((event) => (
            <TrailStep
              key={event.id}
              entry={event}
              focused={event.id === trail.focus.id}
              onSelect={onSelect}
            />
          ))}
        </ol>
        {trail.truncated ? <p className="text-xs text-text-muted">{t('truncated')}</p> : null}
      </section>
    </div>
  );
}

function TrailStep({
  entry,
  focused,
  onSelect,
}: {
  entry: ActivityEntry;
  focused: boolean;
  onSelect: (entryId: string) => void;
}) {
  const locale = useLocale();
  const tone = entryTone(entry);
  return (
    <li className="relative -ml-px">
      <button
        type="button"
        onClick={() => onSelect(entry.id)}
        aria-current={focused ? 'step' : undefined}
        className={cn(
          'flex w-full items-start gap-3 rounded-r-md border-l-2 py-1.5 pl-3 pr-2 text-left hover:bg-surface-muted focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20',
          focused ? 'border-primary bg-primary-soft/60' : 'border-transparent',
        )}
      >
        <span className="w-16 shrink-0 font-mono text-xs text-text-muted">
          {formatTimePrecise(entry.at, locale)}
        </span>
        <KindIcon
          entry={entry}
          className={cn('mt-0.5', tone === 'danger' ? 'text-danger' : 'text-text-muted')}
        />
        <span className="flex min-w-0 flex-1 flex-col">
          <span className="break-all font-mono text-xs">{entryTarget(entry)}</span>
          {entry.page && entry.kind === 'request' ? (
            <span className="truncate text-[0.6875rem] text-text-muted">{entry.page}</span>
          ) : null}
        </span>
        {entry.kind === 'request' ? <Badge tone={tone}>{entry.status ?? '—'}</Badge> : null}
      </button>
    </li>
  );
}

function EntryDetails({ entry }: { entry: ActivityEntry }) {
  const t = useTranslations('adminActivity.trail');
  const locale = useLocale();
  const objects = entryObjects(entry);
  return (
    <section className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-2">
        <OutcomeBadge entry={entry} />
        <span className="break-all font-mono text-sm font-semibold">{entryTarget(entry)}</span>
      </div>
      <dl className="grid grid-cols-[max-content_minmax(0,1fr)] gap-x-4 gap-y-1.5 text-sm">
        <Detail label={t('at')}>{formatDateTimePrecise(entry.at, locale)}</Detail>
        <Detail label={t('actor')}>
          {entry.actorName ? `${entry.actorName} · ${entry.actorEmail ?? ''}` : t('anonymous')}
        </Detail>
        <Detail label={t('ip')} mono>
          {entry.ip}
        </Detail>
        <Detail label={t('page')} mono>
          {entry.page}
        </Detail>
        <Detail label={t('path')} mono>
          {entry.path}
        </Detail>
        <Detail label={t('objects')} mono>
          {objects.length > 0 ? objects.join(' · ') : null}
        </Detail>
        <Detail label={t('handler')} mono>
          {entry.handler}
        </Detail>
        <Detail label={t('requestId')} mono>
          {entry.requestId ? <CopyValue value={entry.requestId} /> : null}
        </Detail>
        <Detail label={t('session')} mono>
          {entry.sessionId}
        </Detail>
        <Detail label={t('browser')}>{entry.userAgent}</Detail>
      </dl>
      <ErrorDetails entry={entry} />
    </section>
  );
}

function ErrorDetails({ entry }: { entry: ActivityEntry }) {
  const t = useTranslations('adminActivity.trail');
  if (!entry.errorCode && !entry.errorMessage) return null;
  return (
    <div className="flex flex-col gap-2 rounded-md border border-danger/30 bg-danger-soft/40 p-3">
      <p className="text-label-md uppercase text-danger">{t('error')}</p>
      <p className="break-all font-mono text-xs">
        {[entry.errorCode, entry.errorType].filter(Boolean).join(' · ')}
      </p>
      {entry.errorMessage ? (
        <p className="whitespace-pre-wrap text-sm">{entry.errorMessage}</p>
      ) : null}
      {entry.errorStack ? (
        <details>
          <summary className="cursor-pointer text-xs font-semibold text-danger">
            {t('stack')}
          </summary>
          <pre className="mt-2 max-h-80 overflow-auto rounded bg-surface p-3 text-[0.6875rem] leading-relaxed">
            {entry.errorStack}
          </pre>
        </details>
      ) : null}
    </div>
  );
}

function Detail({ label, mono, children }: { label: string; mono?: boolean; children: ReactNode }) {
  if (children === null || children === undefined || children === '') return null;
  return (
    <>
      <dt className="text-text-muted">{label}</dt>
      <dd className={cn('min-w-0 break-all', mono && 'font-mono text-xs leading-5')}>{children}</dd>
    </>
  );
}

function CopyValue({ value }: { value: string }) {
  const t = useTranslations('adminActivity.trail');
  const copy = async () => {
    const ok = await copyToClipboard(value);
    toast({ tone: ok ? 'success' : 'error', title: ok ? t('copied') : t('copyFailed') });
  };
  return (
    <span className="flex items-center gap-1">
      {value}
      <Button variant="ghost" size="icon-sm" aria-label={t('copy')} onClick={() => void copy()}>
        <Copy aria-hidden />
      </Button>
    </span>
  );
}
