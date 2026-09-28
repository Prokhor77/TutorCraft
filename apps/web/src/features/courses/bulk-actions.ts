import { coursesApi } from '@/lib/api/endpoints/courses';
import type { Visibility } from '@/lib/api/schemas/common';
import type { ItemSettings } from '@/lib/api/schemas/courses';
import { addDays } from '@/lib/utils/time';

/** Contract has no bulk endpoints: bulk actions are sequential per-item calls (reported as a result summary). */
export type BulkResult = { succeeded: number; failed: number };

async function runSequential(
  ids: string[],
  action: (id: string) => Promise<unknown>,
): Promise<BulkResult> {
  const result: BulkResult = { succeeded: 0, failed: 0 };
  for (const id of ids) {
    try {
      await action(id);
      result.succeeded += 1;
    } catch (error) {
      console.warn('[bulk] item action failed', error instanceof Error ? error.name : 'unknown');
      result.failed += 1;
    }
  }
  return result;
}

export function bulkSetVisibility(
  items: { id: string; version: number }[],
  visibility: Visibility,
): Promise<BulkResult> {
  const versions = new Map(items.map((item) => [item.id, item.version]));
  return runSequential([...versions.keys()], (id) =>
    coursesApi.updateItem(id, { visibility, version: versions.get(id) ?? 0 }),
  );
}

export function bulkMove(
  ids: string[],
  moduleId: string,
  startPosition: number,
): Promise<BulkResult> {
  let position = startPosition;
  return runSequential(ids, (id) => coursesApi.moveItem(id, { moduleId, position: position++ }));
}

const DATE_FIELDS = ['dueAt', 'openAt', 'closeAt'] as const;

/** Shifts every date setting of the item by `days` (assignment/quiz dates). */
export function shiftSettingsDates(settings: ItemSettings, days: number): ItemSettings {
  const shifted: Record<string, unknown> = { ...settings };
  for (const field of DATE_FIELDS) {
    const value = shifted[field];
    if (typeof value === 'string') shifted[field] = addDays(value, days);
  }
  return shifted as ItemSettings;
}

export function bulkShiftDates(ids: string[], days: number): Promise<BulkResult> {
  return runSequential(ids, async (id) => {
    const item = await coursesApi.getItem(id);
    const settings = shiftSettingsDates(item.settings, days);
    const publishAt = item.publishAt ? addDays(item.publishAt, days) : undefined;
    return coursesApi.updateItem(id, { settings, publishAt, version: item.version });
  });
}
