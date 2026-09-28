import { describe, expect, it } from 'vitest';
import {
  createServerClock,
  formatDuration,
  remainingMs,
  serverNowMs,
  TIMER_CRITICAL_MS,
  TIMER_WARNING_MS,
  timerLevel,
} from './timer';

describe('server-synced quiz timer (FR-QUIZ-03, AC-4)', () => {
  it('ignores a skewed client clock by using the serverNow offset', () => {
    const serverNow = '2026-09-27T12:00:00.000Z';
    const clientNowAtFetch = Date.parse('2026-09-27T11:00:00.000Z'); // client is 1 hour behind
    const clock = createServerClock(serverNow, clientNowAtFetch);
    expect(serverNowMs(clock, clientNowAtFetch)).toBe(Date.parse(serverNow));
    const tenMinutesLater = clientNowAtFetch + 10 * 60_000;
    expect(remainingMs('2026-09-27T12:20:00.000Z', clock, tenMinutesLater)).toBe(10 * 60_000);
  });

  it('never goes below zero and returns null without a limit', () => {
    const clock = createServerClock('2026-09-27T12:00:00.000Z', 0);
    expect(remainingMs('2026-09-27T11:00:00.000Z', clock, 0)).toBe(0);
    expect(remainingMs(null, clock, 0)).toBeNull();
  });

  it('computes warning levels', () => {
    expect(timerLevel(null)).toBe('normal');
    expect(timerLevel(TIMER_WARNING_MS + 1)).toBe('normal');
    expect(timerLevel(TIMER_WARNING_MS)).toBe('warning');
    expect(timerLevel(TIMER_CRITICAL_MS)).toBe('critical');
    expect(timerLevel(0)).toBe('expired');
  });

  it('formats durations', () => {
    expect(formatDuration(0)).toBe('0:00');
    expect(formatDuration(4 * 60_000 + 7_000)).toBe('4:07');
    expect(formatDuration(3_909_000)).toBe('1:05:09');
    expect(formatDuration(1_500)).toBe('0:02');
  });
});
