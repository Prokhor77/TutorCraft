'use client';
import { useQueryClient } from '@tanstack/react-query';
import { CalendarClock, Eye, EyeOff, FolderInput, X } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input, NativeSelect } from '@/components/ui/input';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { toast } from '@/components/ui/toast';
import {
  bulkMove,
  bulkSetVisibility,
  bulkShiftDates,
  type BulkResult,
} from '@/features/courses/bulk-actions';
import { findItem, flattenModules } from '@/features/courses/use-outline';
import { queryKeys } from '@/features/query-keys';
import type { CourseOutline } from '@/lib/api/schemas/courses';

type Props = {
  courseId: string;
  outline: CourseOutline;
  selectedIds: string[];
  onClear: () => void;
};

/** Bulk actions (SPEC §10): hide / show / move / shift dates — sequential calls (no bulk endpoints in contract). */
export function BulkBar({ courseId, outline, selectedIds, onClear }: Props) {
  const t = useTranslations('bulk');
  const queryClient = useQueryClient();
  const [busy, setBusy] = useState(false);
  const [days, setDays] = useState('7');
  const modules = flattenModules(outline.modules);
  const [targetModule, setTargetModule] = useState(modules[0]?.id ?? '');

  const run = async (action: () => Promise<BulkResult>) => {
    setBusy(true);
    try {
      const result = await action();
      toast({
        tone: result.failed ? 'error' : 'success',
        title: t('result', { succeeded: result.succeeded, failed: result.failed }),
      });
      onClear();
    } finally {
      setBusy(false);
      void queryClient.invalidateQueries({ queryKey: queryKeys.outline(courseId) });
    }
  };
  const selectedWithVersions = selectedIds.flatMap((id) => {
    const found = findItem(outline, id);
    return found ? [{ id, version: found.item.version }] : [];
  });

  return (
    <div
      role="region"
      aria-label={t('label')}
      className="glass sticky bottom-[calc(var(--size-bottom-nav)+0.5rem)] z-20 flex flex-wrap items-center gap-2 rounded-md border border-card-border p-3 shadow-lg md:bottom-4"
    >
      <span className="text-sm font-medium" aria-live="polite">
        {t('selected', { count: selectedIds.length })}
      </span>
      <Button
        size="sm"
        variant="secondary"
        loading={busy}
        onClick={() => run(() => bulkSetVisibility(selectedWithVersions, 'hidden'))}
      >
        <EyeOff aria-hidden /> {t('hide')}
      </Button>
      <Button
        size="sm"
        variant="secondary"
        disabled={busy}
        onClick={() => run(() => bulkSetVisibility(selectedWithVersions, 'published'))}
      >
        <Eye aria-hidden /> {t('show')}
      </Button>
      <Popover>
        <PopoverTrigger asChild>
          <Button size="sm" variant="secondary" disabled={busy}>
            <FolderInput aria-hidden /> {t('move')}
          </Button>
        </PopoverTrigger>
        <PopoverContent className="flex flex-col gap-3">
          <NativeSelect
            aria-label={t('targetModule')}
            value={targetModule}
            onChange={(event) => setTargetModule(event.target.value)}
          >
            {modules.map((module) => (
              <option key={module.id} value={module.id}>
                {module.title}
              </option>
            ))}
          </NativeSelect>
          <Button
            size="sm"
            onClick={() =>
              run(() =>
                bulkMove(
                  selectedIds,
                  targetModule,
                  modules.find((module) => module.id === targetModule)?.items.length ?? 0,
                ),
              )
            }
          >
            {t('apply')}
          </Button>
        </PopoverContent>
      </Popover>
      <Popover>
        <PopoverTrigger asChild>
          <Button size="sm" variant="secondary" disabled={busy}>
            <CalendarClock aria-hidden /> {t('shiftDates')}
          </Button>
        </PopoverTrigger>
        <PopoverContent className="flex flex-col gap-3">
          <label className="flex flex-col gap-1 text-sm">
            {t('days')}
            <Input type="number" value={days} onChange={(event) => setDays(event.target.value)} />
          </label>
          <p className="text-xs text-text-muted">{t('daysHint')}</p>
          <Button
            size="sm"
            disabled={!Number(days)}
            onClick={() => run(() => bulkShiftDates(selectedIds, Number(days)))}
          >
            {t('apply')}
          </Button>
        </PopoverContent>
      </Popover>
      <Button
        size="icon-sm"
        variant="ghost"
        className="ml-auto"
        onClick={onClear}
        aria-label={t('clear')}
      >
        <X aria-hidden />
      </Button>
    </div>
  );
}
