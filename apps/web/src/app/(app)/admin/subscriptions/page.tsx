'use client';
import { AlarmClock, Building2, CalendarClock, CalendarPlus, Hourglass, Lock } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { useState } from 'react';
import { AdminTablePanel } from '@/components/admin/admin-panel';
import { Badge, type BadgeTone } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { DatePicker, formatDateValue, parseDateValue } from '@/components/ui/date-picker';
import { Dialog, DialogContent, DialogFooter } from '@/components/ui/dialog';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { Field } from '@/components/ui/field';
import { Segmented } from '@/components/ui/segmented';
import { SkeletonList } from '@/components/ui/skeleton';
import { StatCard } from '@/components/ui/stat-card';
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { toast } from '@/components/ui/toast';
import {
  usePlatformSubscriptions,
  usePlatformTenants,
  useSetTrialEnd,
} from '@/features/admin/use-admin';
import type { PlatformSubscription, PlatformTenant } from '@/lib/api/endpoints/platform';
import { formatDate, formatDateTime, fullName } from '@/lib/utils/format';

const DAY_MS = 24 * 60 * 60 * 1000;
const RECENT_DAYS = 30;
const ENDING_SOON_DAYS = 7;
const DEFAULT_EXTENSION_DAYS = 14;
const QUICK_EXTENSIONS = [7, 14, 30, 90] as const;
const FILTERS = ['all', 'trial', 'ending', 'active', 'expired'] as const;

type Filter = (typeof FILTERS)[number];
type Status = PlatformSubscription['status'] | 'not_started';
type SchoolRow = { tenant: PlatformTenant; subscription: PlatformSubscription | undefined };

const STATUS_TONE: Record<Status, BadgeTone> = {
  trial: 'info',
  active: 'success',
  expired: 'danger',
  not_started: 'neutral',
};

/** Whole days from now until `iso` (negative — already past). */
function daysUntil(iso: string, now: number): number {
  return Math.ceil((new Date(iso).getTime() - now) / DAY_MS);
}

function statusOf(row: SchoolRow): Status {
  return row.subscription?.status ?? 'not_started';
}

function endingSoon(row: SchoolRow, now: number): boolean {
  return (
    row.subscription?.status === 'trial' &&
    daysUntil(row.subscription.trialEndsAt, now) <= ENDING_SOON_DAYS
  );
}

function matches(row: SchoolRow, filter: Filter, now: number): boolean {
  if (filter === 'all') return true;
  if (filter === 'ending') return endingSoon(row, now);
  return statusOf(row) === filter;
}

/**
 * Who registered a school and when, how long its free (trial) access lasts, and manual changes of that date by the
 * platform administrator. Schools are listed newest first; a school whose owner never opened the dashboard has no
 * trial yet — it starts on the first visit.
 */
export default function AdminSubscriptionsPage() {
  const t = useTranslations('admin.subscriptions');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const tenants = usePlatformTenants();
  const subscriptions = usePlatformSubscriptions();
  const [filter, setFilter] = useState<Filter>('all');
  const [editing, setEditing] = useState<SchoolRow | null>(null);
  const [now] = useState(() => Date.now());

  const byTenant = new Map(subscriptions.data?.map((item) => [item.tenantId, item]));
  const rows: SchoolRow[] = (tenants.data ?? []).map((tenant) => ({
    tenant,
    subscription: byTenant.get(tenant.id),
  }));
  const visible = rows.filter((row) => matches(row, filter, now));
  const count = (predicate: (row: SchoolRow) => boolean) => rows.filter(predicate).length;
  const recent = count(
    (row) => now - new Date(row.tenant.createdAt).getTime() <= RECENT_DAYS * DAY_MS,
  );

  if (tenants.isError || subscriptions.isError) {
    return (
      <ErrorState
        title={t('loadError')}
        retryLabel={tCommon('retry')}
        onRetry={() => {
          void tenants.refetch();
          void subscriptions.refetch();
        }}
      />
    );
  }
  const loading = tenants.isLoading || subscriptions.isLoading;
  const stat = (value: number) => (loading ? '—' : new Intl.NumberFormat(locale).format(value));

  return (
    <div className="flex flex-col gap-6">
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          label={t('totalSchools')}
          icon={Building2}
          value={stat(rows.length)}
          footer={
            <p className="text-xs text-text-muted">
              {t('recentSchools', { count: recent, days: RECENT_DAYS })}
            </p>
          }
        />
        <StatCard
          label={t('onTrial')}
          icon={Hourglass}
          tone="success"
          value={stat(count((row) => statusOf(row) === 'trial'))}
        />
        <StatCard
          label={t('endingSoon')}
          icon={AlarmClock}
          tone="warning"
          value={stat(count((row) => endingSoon(row, now)))}
          footer={
            <p className="text-xs text-text-muted">
              {t('endingSoonHint', { days: ENDING_SOON_DAYS })}
            </p>
          }
        />
        <StatCard
          label={t('expired')}
          icon={Lock}
          tone="danger"
          value={stat(count((row) => statusOf(row) === 'expired'))}
        />
      </div>

      <AdminTablePanel
        title={t('title')}
        count={loading ? undefined : visible.length}
        description={t('description')}
        toolbar={
          <Segmented
            label={t('filterLabel')}
            value={filter}
            onChange={setFilter}
            options={FILTERS.map((value) => ({ value, label: t(`filters.${value}`) }))}
          />
        }
      >
        {loading ? (
          <div className="p-5 sm:p-6">
            <SkeletonList label={tCommon('loading')} />
          </div>
        ) : visible.length === 0 ? (
          <div className="p-5 sm:p-6">
            <EmptyState icon={CalendarClock} title={t('empty')} description={t('emptyText')} />
          </div>
        ) : (
          <Table>
            <THead>
              <tr>
                <TH>{t('school')}</TH>
                <TH className="hidden md:table-cell">{t('owner')}</TH>
                <TH className="hidden sm:table-cell">{t('registeredAt')}</TH>
                <TH>{t('freeUntil')}</TH>
                <TH className="hidden lg:table-cell">{t('paidUntil')}</TH>
                <TH className="text-right">{t('actions')}</TH>
              </tr>
            </THead>
            <TBody>
              {visible.map((row) => (
                <SchoolRowView
                  key={row.tenant.id}
                  row={row}
                  now={now}
                  onEdit={() => setEditing(row)}
                />
              ))}
            </TBody>
          </Table>
        )}
      </AdminTablePanel>

      {editing ? <TrialDialog row={editing} now={now} onClose={() => setEditing(null)} /> : null}
    </div>
  );
}

function SchoolRowView({ row, now, onEdit }: { row: SchoolRow; now: number; onEdit: () => void }) {
  const t = useTranslations('admin.subscriptions');
  const locale = useLocale();
  const { tenant, subscription } = row;
  const status = statusOf(row);
  const trialDays = subscription ? daysUntil(subscription.trialEndsAt, now) : null;
  const registeredDays = -daysUntil(tenant.createdAt, now);

  return (
    <TR>
      <TD>
        <span className="flex min-w-0 flex-col gap-1">
          <span className="truncate font-semibold">{tenant.name}</span>
          <span className="flex flex-wrap items-center gap-2">
            <Badge tone={STATUS_TONE[status]}>{t(`status.${status}`)}</Badge>
            {tenant.status === 'suspended' ? (
              <Badge tone="danger">{t('schoolSuspended')}</Badge>
            ) : null}
          </span>
          {tenant.owner ? (
            <span className="truncate text-xs text-text-muted md:hidden">{tenant.owner.email}</span>
          ) : null}
        </span>
      </TD>
      <TD className="hidden md:table-cell">
        {tenant.owner ? (
          <span className="flex min-w-0 flex-col">
            <span className="truncate text-sm font-medium">{fullName(tenant.owner)}</span>
            <span className="truncate text-xs text-text-muted">{tenant.owner.email}</span>
          </span>
        ) : (
          <span className="text-sm text-text-muted">—</span>
        )}
      </TD>
      <TD className="hidden whitespace-nowrap sm:table-cell">
        <span className="flex flex-col">
          <span className="text-sm">{formatDateTime(tenant.createdAt, locale)}</span>
          <span className="text-xs text-text-muted">
            {t('daysAgo', { count: Math.max(0, registeredDays) })}
          </span>
        </span>
      </TD>
      <TD className="whitespace-nowrap">
        {subscription && trialDays !== null ? (
          <span className="flex flex-col">
            <span className="text-sm font-medium">
              {formatDate(subscription.trialEndsAt, locale)}
            </span>
            <span
              className={
                trialDays <= 0
                  ? 'text-xs text-danger'
                  : trialDays <= ENDING_SOON_DAYS
                    ? 'text-xs font-semibold text-warning'
                    : 'text-xs text-text-muted'
              }
            >
              {trialDays > 0
                ? t('daysLeft', { count: trialDays })
                : t('endedDaysAgo', { count: -trialDays })}
            </span>
          </span>
        ) : (
          <span className="text-xs text-text-muted">{t('notStartedHint')}</span>
        )}
      </TD>
      <TD className="hidden whitespace-nowrap text-sm lg:table-cell">
        {subscription?.paidUntil ? (
          formatDate(subscription.paidUntil, locale)
        ) : (
          <span className="text-text-muted">{t('notPaid')}</span>
        )}
      </TD>
      <TD className="text-right">
        <Button variant="secondary" size="sm" onClick={onEdit}>
          <CalendarPlus aria-hidden /> {t('extend')}
        </Button>
      </TD>
    </TR>
  );
}

/** End of the chosen local day, as an ISO instant. */
function endOfDay(value: string): string | null {
  const date = parseDateValue(value);
  if (!date) return null;
  date.setHours(23, 59, 59, 0);
  return date.toISOString();
}

function TrialDialog({ row, now, onClose }: { row: SchoolRow; now: number; onClose: () => void }) {
  const t = useTranslations('admin.subscriptions');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const setTrialEnd = useSetTrialEnd();
  const { tenant, subscription } = row;
  // Extensions count from the current end of the free access, or from today if it has already ended.
  const base = Math.max(subscription ? new Date(subscription.trialEndsAt).getTime() : now, now);
  const [value, setValue] = useState(() =>
    formatDateValue(new Date(base + DEFAULT_EXTENSION_DAYS * DAY_MS)),
  );
  const trialEndsAt = endOfDay(value);
  const valid = trialEndsAt !== null && new Date(trialEndsAt).getTime() > now;
  const paidLater =
    subscription?.paidUntil && trialEndsAt && subscription.paidUntil > trialEndsAt
      ? subscription.paidUntil
      : null;

  const submit = () => {
    if (!trialEndsAt || !valid) return;
    setTrialEnd.mutate(
      { tenantId: tenant.id, trialEndsAt, version: subscription?.version ?? 0 },
      {
        onSuccess: () => {
          toast({
            tone: 'success',
            title: t('savedToast', { name: tenant.name, date: formatDate(trialEndsAt, locale) }),
          });
          onClose();
        },
      },
    );
  };

  return (
    <Dialog open onOpenChange={(open) => (open ? undefined : onClose())}>
      <DialogContent
        title={t('dialogTitle', { name: tenant.name })}
        description={
          subscription
            ? t('dialogCurrent', { date: formatDateTime(subscription.trialEndsAt, locale) })
            : t('dialogNotStarted')
        }
        closeLabel={tCommon('close')}
      >
        <form
          noValidate
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            event.preventDefault();
            submit();
          }}
        >
          <div className="flex flex-wrap gap-2" role="group" aria-label={t('quickLabel')}>
            {QUICK_EXTENSIONS.map((days) => (
              <Button
                key={days}
                type="button"
                variant="secondary"
                size="sm"
                onClick={() => setValue(formatDateValue(new Date(base + days * DAY_MS)))}
              >
                {t('plusDays', { count: days })}
              </Button>
            ))}
          </div>
          <Field
            label={t('newDate')}
            hint={t('newDateHint')}
            error={value && !valid ? t('dateInPast') : undefined}
            required
          >
            <DatePicker
              value={value}
              onChange={setValue}
              placeholder={t('datePlaceholder')}
              todayLabel={t('today')}
            />
          </Field>
          {paidLater ? (
            <p className="rounded-md bg-info-soft p-3 text-sm text-info">
              {t('paidLaterNote', { date: formatDate(paidLater, locale) })}
            </p>
          ) : null}
          <DialogFooter>
            <Button type="button" variant="secondary" onClick={onClose}>
              {tCommon('cancel')}
            </Button>
            <Button type="submit" loading={setTrialEnd.isPending} disabled={!valid}>
              {tCommon('save')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
