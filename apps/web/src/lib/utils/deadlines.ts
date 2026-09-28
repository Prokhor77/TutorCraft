import { DAYS_PER_WEEK, zonedDayNumber, zonedParts } from './time';

export const DEADLINE_GROUPS = ['overdue', 'today', 'thisWeek', 'later'] as const;
export type DeadlineGroup = (typeof DEADLINE_GROUPS)[number];

export type HasDue = { dueAt: string | null };

/**
 * FR-DASH-01 / AC-9 grouping: overdue (due < now), today (same local day), this week
 * (until end of the local ISO week, Mon–Sun), later (after this week or no due date).
 */
export function deadlineGroupOf(dueAt: string | null, now: Date, timeZone: string): DeadlineGroup {
  if (!dueAt) return 'later';
  const due = new Date(dueAt);
  if (due.getTime() < now.getTime()) return 'overdue';
  const dueDay = zonedDayNumber(due, timeZone);
  const today = zonedDayNumber(now, timeZone);
  if (dueDay === today) return 'today';
  const daysLeftInWeek = DAYS_PER_WEEK - 1 - zonedParts(now, timeZone).weekday;
  return dueDay <= today + daysLeftInWeek ? 'thisWeek' : 'later';
}

export function groupByDeadline<T extends HasDue>(
  entries: T[],
  now: Date,
  timeZone: string,
): Record<DeadlineGroup, T[]> {
  const groups: Record<DeadlineGroup, T[]> = { overdue: [], today: [], thisWeek: [], later: [] };
  for (const entry of entries) groups[deadlineGroupOf(entry.dueAt, now, timeZone)].push(entry);
  for (const group of DEADLINE_GROUPS) groups[group].sort(compareByDue);
  return groups;
}

export function compareByDue(a: HasDue, b: HasDue): number {
  if (a.dueAt === b.dueAt) return 0;
  if (!a.dueAt) return 1;
  if (!b.dueAt) return -1;
  return new Date(a.dueAt).getTime() - new Date(b.dueAt).getTime();
}
