import type { BadgeTone } from '@/components/ui/badge';
import type { ActivityEntry } from '@/lib/api/schemas/activity';

const CLIENT_ERROR_STATUS = 400;
const SERVER_ERROR_STATUS = 500;
const SLOW_REQUEST_MS = 1_000;

/** Severity of a record: server/browser errors are red, rejected requests amber, the rest calm. */
export function entryTone(entry: Pick<ActivityEntry, 'kind' | 'status'>): BadgeTone {
  if (entry.kind === 'client_error') return 'danger';
  if (entry.kind === 'page_view') return 'info';
  if (entry.status === null) return 'neutral';
  if (entry.status >= SERVER_ERROR_STATUS) return 'danger';
  if (entry.status >= CLIENT_ERROR_STATUS) return 'warning';
  return 'success';
}

export function isFailure(entry: Pick<ActivityEntry, 'kind' | 'status'>): boolean {
  return entry.kind === 'client_error' || (entry.status ?? 0) >= CLIENT_ERROR_STATUS;
}

export function isSlow(entry: Pick<ActivityEntry, 'durationMs'>): boolean {
  return (entry.durationMs ?? 0) >= SLOW_REQUEST_MS;
}

/** «POST /api/v1/courses/{courseId}» for requests, the page for views, the error type for browser errors. */
export function entryTarget(
  entry: Pick<ActivityEntry, 'kind' | 'method' | 'route' | 'path' | 'page' | 'errorType'>,
): string {
  if (entry.kind === 'page_view') return entry.page ?? '/';
  if (entry.kind === 'client_error') return entry.errorType ?? entry.page ?? '—';
  const target = entry.route ?? entry.path ?? '—';
  return entry.method ? `${entry.method} ${target}` : target;
}

/** Object ids from the path (`courseId → …`), shown under the route so the admin sees which object was touched. */
export function entryObjects(entry: Pick<ActivityEntry, 'pathParams'>): string[] {
  return Object.entries(entry.pathParams ?? {}).map(([name, value]) => `${name}: ${value}`);
}
