import { describe, expect, it, vi } from 'vitest';
import { ApiProblem } from '@/lib/api/problem';
import {
  localStorageQueueStorage,
  memoryQueueStorage,
  OfflineQueue,
  type QueuedOperation,
} from './queue';
import { isRetryableError } from './queues';

function makeQueue(onPermanentFailure = vi.fn()) {
  const queue = new OfflineQueue(memoryQueueStorage(), {
    isRetryable: isRetryableError,
    onPermanentFailure,
  });
  return { queue, onPermanentFailure };
}

const networkError = new ApiProblem({ status: 0, code: 'client.network', title: 'offline' });
const validationError = new ApiProblem({ status: 422, code: 'submission.closed', title: 'closed' });

describe('OfflineQueue (NFR-REL-05, AC-3)', () => {
  it('keeps the original Idempotency-Key when the same operation is enqueued again', () => {
    const { queue } = makeQueue();
    queue.enqueue({
      kind: 'submit',
      dedupeKey: 'submit:i1',
      payload: { itemId: 'i1' },
      idempotencyKey: 'key-1',
    });
    const again = queue.enqueue({
      kind: 'submit',
      dedupeKey: 'submit:i1',
      payload: { itemId: 'i1' },
      idempotencyKey: 'key-2',
    });
    expect(again.idempotencyKey).toBe('key-1');
    expect(queue.list()).toHaveLength(1);
  });

  it('retries a network failure later with the same key → exactly one successful call', async () => {
    const { queue } = makeQueue();
    queue.enqueue({
      kind: 'submit',
      dedupeKey: 'submit:i1',
      payload: { itemId: 'i1' },
      idempotencyKey: 'key-1',
    });
    const seenKeys: string[] = [];
    let online = false;
    const handler = vi.fn(async (operation: QueuedOperation) => {
      seenKeys.push(operation.idempotencyKey);
      if (!online) throw networkError;
    });

    const first = await queue.flush(handler);
    expect(first).toEqual({ succeeded: 0, failed: 0, pending: 1 });
    expect(queue.list()[0]?.attempts).toBe(1);

    online = true;
    const second = await queue.flush(handler);
    expect(second).toEqual({ succeeded: 1, failed: 0, pending: 0 });
    expect(seenKeys).toEqual(['key-1', 'key-1']);
  });

  it('drops operations with non-retryable errors and reports them', async () => {
    const { queue, onPermanentFailure } = makeQueue();
    queue.enqueue({ kind: 'submit', dedupeKey: 'a', payload: 1, idempotencyKey: 'k1' });
    queue.enqueue({ kind: 'submit', dedupeKey: 'b', payload: 2, idempotencyKey: 'k2' });
    const handler = vi.fn(async (operation: QueuedOperation) => {
      if (operation.dedupeKey === 'a') throw validationError;
    });
    const outcome = await queue.flush(handler);
    expect(outcome).toEqual({ succeeded: 1, failed: 1, pending: 0 });
    expect(onPermanentFailure).toHaveBeenCalledWith(
      expect.objectContaining({ dedupeKey: 'a' }),
      validationError,
    );
  });

  it('stops at the first retryable failure to preserve ordering', async () => {
    const { queue } = makeQueue();
    queue.enqueue({ kind: 'answer', dedupeKey: 'a', payload: 1, idempotencyKey: 'k1' });
    queue.enqueue({ kind: 'answer', dedupeKey: 'b', payload: 2, idempotencyKey: 'k2' });
    const handler = vi.fn(async () => {
      throw networkError;
    });
    await queue.flush(handler);
    expect(handler).toHaveBeenCalledTimes(1);
    expect(queue.list()).toHaveLength(2);
  });

  it('keeps a newer payload enqueued while the previous one was in flight', async () => {
    const { queue } = makeQueue();
    queue.enqueue({
      kind: 'answer',
      dedupeKey: 'answer:a1:1',
      payload: { v: 1 },
      idempotencyKey: 'k1',
    });
    const handler = vi.fn(async () => {
      queue.enqueue({
        kind: 'answer',
        dedupeKey: 'answer:a1:1',
        payload: { v: 2 },
        idempotencyKey: 'k2',
      });
    });
    await queue.flush(handler);
    expect(queue.list().map((operation) => operation.payload)).toEqual([{ v: 2 }]);
  });

  it('runs a single flush at a time', async () => {
    const { queue } = makeQueue();
    queue.enqueue({ kind: 'x', dedupeKey: 'a', payload: 1, idempotencyKey: 'k1' });
    const handler = vi.fn(() => new Promise<void>((resolve) => setTimeout(resolve, 5)));
    const [a, b] = await Promise.all([queue.flush(handler), queue.flush(handler)]);
    expect(handler).toHaveBeenCalledTimes(1);
    expect(a).toBe(b);
  });

  it('notifies subscribers on change', () => {
    const { queue } = makeQueue();
    const listener = vi.fn();
    const unsubscribe = queue.subscribe(listener);
    queue.enqueue({ kind: 'x', dedupeKey: 'a', payload: 1, idempotencyKey: 'k1' });
    unsubscribe();
    queue.enqueue({ kind: 'x', dedupeKey: 'b', payload: 1, idempotencyKey: 'k2' });
    expect(listener).toHaveBeenCalledTimes(1);
  });
});

describe('localStorageQueueStorage', () => {
  it('persists across instances (survives reloads)', () => {
    const storage = localStorageQueueStorage('test-queue');
    new OfflineQueue(storage, { isRetryable: () => true }).enqueue({
      kind: 'x',
      dedupeKey: 'a',
      payload: 1,
      idempotencyKey: 'k1',
    });
    expect(
      new OfflineQueue(localStorageQueueStorage('test-queue'), { isRetryable: () => true }).list(),
    ).toHaveLength(1);
  });

  it('recovers from corrupted storage', () => {
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    localStorage.setItem('broken', '{not json');
    expect(localStorageQueueStorage('broken').load()).toEqual([]);
  });
});

describe('isRetryableError', () => {
  it('retries network errors, 5xx and 429, but not validation errors', () => {
    expect(isRetryableError(networkError)).toBe(true);
    expect(isRetryableError(new ApiProblem({ status: 503, code: 'x', title: 'x' }))).toBe(true);
    expect(isRetryableError(new ApiProblem({ status: 429, code: 'x', title: 'x' }))).toBe(true);
    expect(isRetryableError(validationError)).toBe(false);
    expect(isRetryableError(new Error('unknown'))).toBe(true);
  });
});
