import { MS_PER_DAY, MS_PER_HOUR, MS_PER_MINUTE } from './time';

export function formatDateTime(iso: string, locale: string, timeZone?: string): string {
  return new Intl.DateTimeFormat(locale, {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone,
  }).format(new Date(iso));
}

export function formatDate(iso: string, locale: string, timeZone?: string): string {
  return new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeZone }).format(new Date(iso));
}

export function formatTime(iso: string, locale: string, timeZone?: string): string {
  return new Intl.DateTimeFormat(locale, { timeStyle: 'short', timeZone }).format(new Date(iso));
}

/** Time with seconds: ordering of events inside one minute matters in logs. */
export function formatTimePrecise(iso: string, locale: string, timeZone?: string): string {
  return new Intl.DateTimeFormat(locale, { timeStyle: 'medium', timeZone }).format(new Date(iso));
}

/** Date + time with seconds (activity log). */
export function formatDateTimePrecise(iso: string, locale: string, timeZone?: string): string {
  return new Intl.DateTimeFormat(locale, {
    dateStyle: 'medium',
    timeStyle: 'medium',
    timeZone,
  }).format(new Date(iso));
}

/** "через 3 часа" / "2 days ago" — NFR-I18N-02. */
export function formatRelative(iso: string, locale: string, now: Date = new Date()): string {
  const diff = new Date(iso).getTime() - now.getTime();
  const abs = Math.abs(diff);
  const rtf = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' });
  if (abs < MS_PER_HOUR) return rtf.format(Math.round(diff / MS_PER_MINUTE), 'minute');
  if (abs < MS_PER_DAY) return rtf.format(Math.round(diff / MS_PER_HOUR), 'hour');
  return rtf.format(Math.round(diff / MS_PER_DAY), 'day');
}

const BYTES_PER_KB = 1024;
const SIZE_UNITS = ['B', 'KB', 'MB', 'GB'] as const;

export function formatFileSize(bytes: number, locale: string): string {
  let value = bytes;
  let unitIndex = 0;
  while (value >= BYTES_PER_KB && unitIndex < SIZE_UNITS.length - 1) {
    value /= BYTES_PER_KB;
    unitIndex += 1;
  }
  return `${new Intl.NumberFormat(locale, { maximumFractionDigits: 1 }).format(value)} ${SIZE_UNITS[unitIndex]}`;
}

export function formatScore(score: number | null, maxScore: number, locale: string): string {
  const nf = new Intl.NumberFormat(locale, { maximumFractionDigits: 2 });
  return `${score === null ? '—' : nf.format(score)} / ${nf.format(maxScore)}`;
}

export function formatPercent(percent: number | null, locale: string): string {
  if (percent === null) return '—';
  return new Intl.NumberFormat(locale, { style: 'percent', maximumFractionDigits: 1 }).format(
    percent / 100,
  );
}

export function fullName(person: { firstName: string; lastName: string }): string {
  return `${person.firstName} ${person.lastName}`.trim();
}

export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('');
}
