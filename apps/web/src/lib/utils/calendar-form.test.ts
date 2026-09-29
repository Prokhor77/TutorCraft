import { describe, expect, it } from 'vitest';
import type { OutlineModule } from '@/lib/api/schemas/courses';
import {
  combineLocal,
  flattenOutline,
  isTimeBefore,
  toDateInputValue,
  toTimeInputValue,
} from './calendar-form';

const outlineModule = (
  id: string,
  position: number,
  extra: Partial<OutlineModule> = {},
): OutlineModule =>
  ({
    id,
    parentId: null,
    title: `Module ${id}`,
    position,
    visibility: 'published',
    publishAt: null,
    availability: { available: true },
    items: [],
    children: [],
    version: 0,
    ...extra,
  }) as OutlineModule;

describe('calendar form helpers', () => {
  it('round-trips local date and time inputs', () => {
    const date = new Date(2026, 9, 5, 9, 7);
    expect(toDateInputValue(date)).toBe('2026-10-05');
    expect(toTimeInputValue(date)).toBe('09:07');
    expect(combineLocal('2026-10-05', '09:07')).toBe(date.toISOString());
  });

  it('rejects incomplete date/time and detects reversed ranges', () => {
    expect(combineLocal('', '10:00')).toBeNull();
    expect(combineLocal('2026-10-05', '')).toBeNull();
    expect(isTimeBefore('09:00', '10:00')).toBe(true);
    expect(isTimeBefore('11:00', '10:00')).toBe(false);
    expect(isTimeBefore('', '10:00')).toBe(false);
  });

  it('flattens nested modules in position order with their items', () => {
    const child = outlineModule('c', 0, {
      items: [{ id: 'i2', title: 'Item 2' } as OutlineModule['items'][number]],
    });
    const outline = [
      outlineModule('b', 1),
      outlineModule('a', 0, {
        items: [{ id: 'i1', title: 'Item 1' } as OutlineModule['items'][number]],
        children: [child],
      }),
    ];

    const { modules, items } = flattenOutline(outline);

    expect(modules.map((entry) => [entry.id, entry.depth])).toEqual([
      ['a', 0],
      ['c', 1],
      ['b', 0],
    ]);
    expect(items).toEqual([
      { id: 'i1', title: 'Item 1', moduleId: 'a' },
      { id: 'i2', title: 'Item 2', moduleId: 'c' },
    ]);
  });
});
