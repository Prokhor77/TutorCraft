import { DAYS_PER_WEEK, MS_PER_DAY } from './time';

export type CalendarView = 'month' | 'week' | 'list';
const MONTH_GRID_WEEKS = 6;
const LIST_RANGE_DAYS = 30;

/** Monday-based start of the week containing `date` (local calendar). */
export function startOfWeek(date: Date): Date {
  const result = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const mondayIndex = (result.getDay() + DAYS_PER_WEEK - 1) % DAYS_PER_WEEK;
  result.setDate(result.getDate() - mondayIndex);
  return result;
}

export function addLocalDays(date: Date, days: number): Date {
  const result = new Date(date);
  result.setDate(result.getDate() + days);
  return result;
}

/** 6×7 grid of days covering the month of `anchor`. */
export function monthGrid(anchor: Date): Date[][] {
  const first = new Date(anchor.getFullYear(), anchor.getMonth(), 1);
  const start = startOfWeek(first);
  return Array.from({ length: MONTH_GRID_WEEKS }, (_, week) =>
    Array.from({ length: DAYS_PER_WEEK }, (_, day) =>
      addLocalDays(start, week * DAYS_PER_WEEK + day),
    ),
  );
}

export function weekDays(anchor: Date): Date[] {
  const start = startOfWeek(anchor);
  return Array.from({ length: DAYS_PER_WEEK }, (_, day) => addLocalDays(start, day));
}

/** [from, to) instants for the API query of a given view. */
export function rangeFor(view: CalendarView, anchor: Date): { from: Date; to: Date } {
  if (view === 'month') {
    const grid = monthGrid(anchor);
    const first = grid[0]?.[0] ?? anchor;
    return { from: first, to: addLocalDays(first, MONTH_GRID_WEEKS * DAYS_PER_WEEK) };
  }
  if (view === 'week') {
    const start = startOfWeek(anchor);
    return { from: start, to: addLocalDays(start, DAYS_PER_WEEK) };
  }
  const start = new Date(anchor.getFullYear(), anchor.getMonth(), anchor.getDate());
  return { from: start, to: new Date(start.getTime() + LIST_RANGE_DAYS * MS_PER_DAY) };
}

export function shiftAnchor(view: CalendarView, anchor: Date, direction: 1 | -1): Date {
  if (view === 'month') return new Date(anchor.getFullYear(), anchor.getMonth() + direction, 1);
  if (view === 'week') return addLocalDays(anchor, direction * DAYS_PER_WEEK);
  return addLocalDays(anchor, direction * LIST_RANGE_DAYS);
}

export function sameLocalDay(a: Date, b: Date): boolean {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  );
}

export function dayKey(date: Date): string {
  return `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`;
}
