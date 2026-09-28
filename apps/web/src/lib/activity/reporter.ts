import { activityApi, type ClientActivityEvent } from '@/lib/api/endpoints/activity';
import { CONTRACT_MISMATCH_EVENT } from '@/lib/api/http';
import { isApiProblem } from '@/lib/api/problem';
import { useAuthStore } from '@/stores/auth-store';
import { ActivityEventBuffer } from './event-buffer';
import { currentPagePath, sanitizePagePath } from './page-path';

const FLUSH_INTERVAL_MS = 5_000;

const buffer = new ActivityEventBuffer({
  send: (events) => activityApi.sendEvents(events),
  canSend: () => Boolean(useAuthStore.getState().accessToken),
  onDropped: (reason) => console.warn(`[activity] ${reason}`),
});

function now(): string {
  return new Date().toISOString();
}

export function trackPageView(pathname: string): void {
  buffer.push({ kind: 'page_view', page: sanitizePagePath(pathname), occurredAt: now() });
}

function describeError(
  error: unknown,
): Pick<ClientActivityEvent, 'name' | 'message' | 'stack' | 'requestId'> {
  if (isApiProblem(error)) {
    return { name: error.code, message: error.detail ?? error.title, requestId: error.requestId };
  }
  if (error instanceof Error)
    return { name: error.name, message: error.message, stack: error.stack };
  return { name: 'NonError', message: typeof error === 'string' ? error : 'Unknown error' };
}

/** Browser-side failure (JS exception, crashed screen, broken API contract) → activity log. Never throws. */
export function reportClientError(error: unknown): void {
  try {
    buffer.push({
      kind: 'client_error',
      page: currentPagePath() ?? '/',
      occurredAt: now(),
      ...describeError(error),
    });
    void buffer.flush();
  } catch (failure) {
    console.warn(
      '[activity] could not report error',
      failure instanceof Error ? failure.name : 'unknown',
    );
  }
}

/**
 * Starts periodic sending plus global error listeners; returns the cleanup. Sends what is left when the tab is hidden
 * (switching tabs, closing), so the last actions before a crash are not lost.
 */
export function startActivityReporting(): () => void {
  const flush = () => void buffer.flush();
  const onVisibility = () => {
    if (document.visibilityState === 'hidden') flush();
  };
  const onError = (event: ErrorEvent) => reportClientError(event.error ?? new Error(event.message));
  const onRejection = (event: PromiseRejectionEvent) => reportClientError(event.reason);
  const onContractMismatch = (event: Event) =>
    reportClientError((event as CustomEvent<unknown>).detail);

  const timer = window.setInterval(flush, FLUSH_INTERVAL_MS);
  document.addEventListener('visibilitychange', onVisibility);
  window.addEventListener('pagehide', flush);
  window.addEventListener('error', onError);
  window.addEventListener('unhandledrejection', onRejection);
  window.addEventListener(CONTRACT_MISMATCH_EVENT, onContractMismatch);
  return () => {
    window.clearInterval(timer);
    document.removeEventListener('visibilitychange', onVisibility);
    window.removeEventListener('pagehide', flush);
    window.removeEventListener('error', onError);
    window.removeEventListener('unhandledrejection', onRejection);
    window.removeEventListener(CONTRACT_MISMATCH_EVENT, onContractMismatch);
  };
}
