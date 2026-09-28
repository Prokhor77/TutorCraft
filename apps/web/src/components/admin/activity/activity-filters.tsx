'use client';
import { Hash, Route, Search } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { FilterChips } from '@/components/admin/admin-panel';
import { Switch } from '@/components/ui/checkbox';
import { DateTimeInput, IconInput, NativeSelect } from '@/components/ui/input';
import {
  ACTIVITY_KINDS,
  ACTIVITY_OUTCOMES,
  type ActivityKind,
  type ActivityOutcome,
} from '@/lib/api/schemas/activity';

export type ActivityFilterState = {
  outcome: ActivityOutcome;
  kind: ActivityKind | 'all';
  actor: string;
  route: string;
  requestId: string;
  from: string;
  to: string;
  includeAnonymous: boolean;
};

export const DEFAULT_ACTIVITY_FILTERS: ActivityFilterState = {
  outcome: 'all',
  kind: 'all',
  actor: '',
  route: '',
  requestId: '',
  from: '',
  to: '',
  includeAnonymous: false,
};

type Props = {
  value: ActivityFilterState;
  onChange: (next: ActivityFilterState) => void;
};

/** Toolbar of the activity log: outcome chips, who / where / request id, period, anonymous toggle. */
export function ActivityFilters({ value, onChange }: Props) {
  const t = useTranslations('adminActivity');
  const set = <K extends keyof ActivityFilterState>(key: K, next: ActivityFilterState[K]) =>
    onChange({ ...value, [key]: next });
  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <FilterChips
          label={t('filters.outcome')}
          value={value.outcome}
          onChange={(outcome) => set('outcome', outcome)}
          options={ACTIVITY_OUTCOMES.map((outcome) => ({
            value: outcome,
            label: t(`outcome.${outcome}`),
          }))}
        />
        <label className="flex items-center gap-2 text-sm text-text-muted">
          <Switch
            checked={value.includeAnonymous}
            onCheckedChange={(checked) => set('includeAnonymous', checked)}
          />
          {t('filters.anonymous')}
        </label>
      </div>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <IconInput
          className="h-11"
          icon={Search}
          aria-label={t('filters.actor')}
          placeholder={t('filters.actor')}
          value={value.actor}
          onChange={(event) => set('actor', event.target.value)}
        />
        <IconInput
          className="h-11"
          icon={Route}
          aria-label={t('filters.route')}
          placeholder={t('filters.route')}
          value={value.route}
          onChange={(event) => set('route', event.target.value)}
        />
        <IconInput
          className="h-11"
          icon={Hash}
          aria-label={t('filters.requestId')}
          placeholder={t('filters.requestId')}
          value={value.requestId}
          onChange={(event) => set('requestId', event.target.value.trim())}
        />
        <NativeSelect
          aria-label={t('filters.kind')}
          value={value.kind}
          onChange={(event) => set('kind', event.target.value as ActivityFilterState['kind'])}
        >
          <option value="all">{t('kind.all')}</option>
          {ACTIVITY_KINDS.map((kind) => (
            <option key={kind} value={kind}>
              {t(`kind.${kind}`)}
            </option>
          ))}
        </NativeSelect>
      </div>
      <div className="flex flex-col gap-3 md:flex-row md:flex-wrap md:items-end">
        <label className="flex items-center gap-2 text-label-md uppercase text-text-muted">
          {t('filters.from')}
          <DateTimeInput
            value={value.from}
            onChange={(event) => set('from', event.target.value)}
            className="normal-case"
          />
        </label>
        <label className="flex items-center gap-2 text-label-md uppercase text-text-muted">
          {t('filters.to')}
          <DateTimeInput
            value={value.to}
            onChange={(event) => set('to', event.target.value)}
            className="normal-case"
          />
        </label>
      </div>
    </div>
  );
}
