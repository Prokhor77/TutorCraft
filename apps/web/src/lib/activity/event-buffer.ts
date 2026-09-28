import type { ClientActivityEvent } from '@/lib/api/endpoints/activity';

export const EVENT_LIMITS = {
  maxBatch: 20,
  maxQueue: 100,
  name: 200,
  message: 2000,
  stack: 8000,
} as const;

const DEFAULT_ERRORS_PER_MINUTE = 10;
const DEFAULT_DEDUPE_WINDOW_MS = 10_000;
const MINUTE_MS = 60_000;

export type ActivityBufferOptions = {
  send: (events: ClientActivityEvent[]) => Promise<void>;
  /** False until a session exists: events wait in the (bounded) queue. */
  canSend: () => boolean;
  now?: () => number;
  errorsPerMinute?: number;
  dedupeWindowMs?: number;
  onDropped?: (reason: string) => void;
};

function clip(value: string | undefined, max: number): string | undefined {
  return value === undefined ? undefined : value.slice(0, max);
}

/**
 * Batches browser events for the activity log. A crash loop must not flood the API, so identical errors within a short
 * window are collapsed and errors per minute are capped; a failed send drops the batch (the log never breaks the UI).
 */
export class ActivityEventBuffer {
  private queue: ClientActivityEvent[] = [];
  private readonly lastSeen = new Map<string, number>();
  private errorTimes: number[] = [];
  private sending = false;
  private readonly now: () => number;

  constructor(private readonly options: ActivityBufferOptions) {
    this.now = options.now ?? (() => Date.now());
  }

  get size(): number {
    return this.queue.length;
  }

  push(event: ClientActivityEvent): boolean {
    if (event.kind === 'client_error' && !this.admitError(event)) return false;
    if (this.queue.length >= EVENT_LIMITS.maxQueue) this.queue.shift();
    this.queue.push({
      ...event,
      name: clip(event.name, EVENT_LIMITS.name),
      message: clip(event.message, EVENT_LIMITS.message),
      stack: clip(event.stack, EVENT_LIMITS.stack),
    });
    return true;
  }

  async flush(): Promise<void> {
    if (this.sending || this.queue.length === 0 || !this.options.canSend()) return;
    this.sending = true;
    const batch = this.queue.splice(0, EVENT_LIMITS.maxBatch);
    try {
      await this.options.send(batch);
    } catch {
      this.options.onDropped?.(`send failed, ${batch.length} events dropped`);
    } finally {
      this.sending = false;
    }
  }

  private admitError(event: ClientActivityEvent): boolean {
    const now = this.now();
    const key = `${event.name ?? ''}|${event.message ?? ''}|${event.page}`;
    const seen = this.lastSeen.get(key);
    if (
      seen !== undefined &&
      now - seen < (this.options.dedupeWindowMs ?? DEFAULT_DEDUPE_WINDOW_MS)
    )
      return false;
    this.errorTimes = this.errorTimes.filter((time) => now - time < MINUTE_MS);
    if (this.errorTimes.length >= (this.options.errorsPerMinute ?? DEFAULT_ERRORS_PER_MINUTE)) {
      this.options.onDropped?.('client error rate limit reached');
      return false;
    }
    this.lastSeen.set(key, now);
    this.errorTimes.push(now);
    return true;
  }
}
