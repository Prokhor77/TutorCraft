import { describe, expect, it } from 'vitest';
import { deadlineGroupOf, groupByDeadline } from './deadlines';

const MOSCOW = 'Europe/Moscow';
// Wednesday 2026-09-30 12:00 in Moscow (UTC+3).
const NOW = new Date('2026-09-30T09:00:00Z');

describe('deadlineGroupOf (FR-DASH-01, AC-9)', () => {
  it('classifies overdue, today, this week and later in the user time zone', () => {
    expect(deadlineGroupOf('2026-09-30T08:59:00Z', NOW, MOSCOW)).toBe('overdue');
    expect(deadlineGroupOf('2026-09-30T20:59:00Z', NOW, MOSCOW)).toBe('today'); // 23:59 MSK
    expect(deadlineGroupOf('2026-09-30T21:01:00Z', NOW, MOSCOW)).toBe('thisWeek'); // 00:01 Thu MSK
    expect(deadlineGroupOf('2026-10-04T20:00:00Z', NOW, MOSCOW)).toBe('thisWeek'); // Sunday 23:00 MSK
    expect(deadlineGroupOf('2026-10-04T21:30:00Z', NOW, MOSCOW)).toBe('later'); // Monday 00:30 MSK
    expect(deadlineGroupOf(null, NOW, MOSCOW)).toBe('later');
  });

  it('depends on the time zone for the "today" boundary', () => {
    const due = '2026-09-30T22:00:00Z';
    expect(deadlineGroupOf(due, NOW, 'UTC')).toBe('today');
    expect(deadlineGroupOf(due, NOW, MOSCOW)).toBe('thisWeek');
  });
});

describe('groupByDeadline', () => {
  it('groups and sorts entries by due date with undated entries last', () => {
    const groups = groupByDeadline(
      [
        { id: 'later-undated', dueAt: null },
        { id: 'today-late', dueAt: '2026-09-30T18:00:00Z' },
        { id: 'today-early', dueAt: '2026-09-30T10:00:00Z' },
        { id: 'overdue', dueAt: '2026-09-29T10:00:00Z' },
        { id: 'next-month', dueAt: '2026-10-20T10:00:00Z' },
      ],
      NOW,
      MOSCOW,
    );
    expect(groups.overdue.map((entry) => entry.id)).toEqual(['overdue']);
    expect(groups.today.map((entry) => entry.id)).toEqual(['today-early', 'today-late']);
    expect(groups.thisWeek).toEqual([]);
    expect(groups.later.map((entry) => entry.id)).toEqual(['next-month', 'later-undated']);
  });
});
