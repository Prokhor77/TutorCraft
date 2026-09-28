import { MS_PER_SECOND, SECONDS_PER_MINUTE } from './time';

/**
 * Server-synced countdown for quiz attempts (FR-QUIZ-03, AC-4).
 * The client never trusts its own clock: it measures the offset between `serverNow`
 * (from the Attempt response) and the local clock at receipt, and counts down to `timeDue`.
 */
export type ServerClock = { offsetMs: number };

export function createServerClock(serverNowIso: string, clientNowMs: number): ServerClock {
  return { offsetMs: new Date(serverNowIso).getTime() - clientNowMs };
}

export function serverNowMs(clock: ServerClock, clientNowMs: number): number {
  return clientNowMs + clock.offsetMs;
}

export function remainingMs(
  timeDueIso: string | null,
  clock: ServerClock,
  clientNowMs: number,
): number | null {
  if (!timeDueIso) return null;
  return Math.max(0, new Date(timeDueIso).getTime() - serverNowMs(clock, clientNowMs));
}

export const TIMER_WARNING_MS = 5 * SECONDS_PER_MINUTE * MS_PER_SECOND;
export const TIMER_CRITICAL_MS = SECONDS_PER_MINUTE * MS_PER_SECOND;
export type TimerLevel = 'normal' | 'warning' | 'critical' | 'expired';

export function timerLevel(remaining: number | null): TimerLevel {
  if (remaining === null) return 'normal';
  if (remaining <= 0) return 'expired';
  if (remaining <= TIMER_CRITICAL_MS) return 'critical';
  if (remaining <= TIMER_WARNING_MS) return 'warning';
  return 'normal';
}

/** "1:05:09" / "4:07" */
export function formatDuration(ms: number): string {
  const totalSeconds = Math.ceil(ms / MS_PER_SECOND);
  const hours = Math.floor(totalSeconds / (SECONDS_PER_MINUTE * SECONDS_PER_MINUTE));
  const minutes = Math.floor(
    (totalSeconds % (SECONDS_PER_MINUTE * SECONDS_PER_MINUTE)) / SECONDS_PER_MINUTE,
  );
  const seconds = totalSeconds % SECONDS_PER_MINUTE;
  const pad = (value: number) => String(value).padStart(2, '0');
  return hours > 0 ? `${hours}:${pad(minutes)}:${pad(seconds)}` : `${minutes}:${pad(seconds)}`;
}
