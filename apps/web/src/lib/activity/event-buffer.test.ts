import { describe, expect, it, vi } from 'vitest';
import type { ClientActivityEvent } from '@/lib/api/endpoints/activity';
import { ActivityEventBuffer, EVENT_LIMITS } from './event-buffer';

const AT = '2026-09-28T12:00:00Z';

function pageView(page: string): ClientActivityEvent {
  return { kind: 'page_view', page, occurredAt: AT };
}

function clientError(message: string): ClientActivityEvent {
  return { kind: 'client_error', page: '/home', name: 'TypeError', message, occurredAt: AT };
}

describe('ActivityEventBuffer', () => {
  it('waits for a session and sends batches of at most maxBatch', async () => {
    let authenticated = false;
    const send = vi.fn(async (_events: ClientActivityEvent[]) => undefined);
    const buffer = new ActivityEventBuffer({ send, canSend: () => authenticated });
    for (let index = 0; index < EVENT_LIMITS.maxBatch + 5; index += 1)
      buffer.push(pageView(`/p${index}`));

    await buffer.flush();
    expect(send).not.toHaveBeenCalled();

    authenticated = true;
    await buffer.flush();
    expect(send).toHaveBeenCalledTimes(1);
    expect(send.mock.calls[0]?.[0]).toHaveLength(EVENT_LIMITS.maxBatch);
    expect(buffer.size).toBe(5);
  });

  it('collapses repeated errors and caps the error rate', () => {
    let now = 0;
    const buffer = new ActivityEventBuffer({
      send: async () => undefined,
      canSend: () => true,
      now: () => now,
      errorsPerMinute: 2,
      dedupeWindowMs: 1000,
    });
    expect(buffer.push(clientError('a'))).toBe(true);
    expect(buffer.push(clientError('a'))).toBe(false);
    now = 2000;
    expect(buffer.push(clientError('a'))).toBe(true);
    expect(buffer.push(clientError('b'))).toBe(false);
    now = 70_000;
    expect(buffer.push(clientError('b'))).toBe(true);
  });

  it('drops a batch when sending fails and trims long fields', async () => {
    const onDropped = vi.fn();
    const send = vi.fn(async (_events: ClientActivityEvent[]) =>
      Promise.reject(new Error('offline')),
    );
    const buffer = new ActivityEventBuffer({ send, canSend: () => true, onDropped });
    buffer.push({ ...clientError('x'), stack: 's'.repeat(EVENT_LIMITS.stack + 10) });
    await buffer.flush();
    expect(send.mock.calls[0]?.[0][0]?.stack).toHaveLength(EVENT_LIMITS.stack);
    expect(onDropped).toHaveBeenCalledOnce();
    expect(buffer.size).toBe(0);
  });

  it('keeps the queue bounded', () => {
    const buffer = new ActivityEventBuffer({ send: async () => undefined, canSend: () => false });
    for (let index = 0; index < EVENT_LIMITS.maxQueue + 10; index += 1)
      buffer.push(pageView(`/p${index}`));
    expect(buffer.size).toBe(EVENT_LIMITS.maxQueue);
  });
});
