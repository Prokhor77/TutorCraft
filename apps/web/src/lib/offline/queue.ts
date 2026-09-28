/**
 * Persistent retry queue for operations that must survive network loss and reloads
 * (NFR-REL-05, AC-3): assignment submit and quiz answer autosave.
 * Operations with the same `dedupeKey` collapse: the newest payload wins, but the ORIGINAL
 * idempotency key is kept so a retried submit can never create a second submission.
 */
export type QueuedOperation<P = unknown> = {
  id: string;
  kind: string;
  dedupeKey: string;
  payload: P;
  idempotencyKey: string;
  createdAt: number;
  attempts: number;
};

export interface QueueStorage {
  load(): QueuedOperation[];
  save(operations: QueuedOperation[]): void;
}

export function memoryQueueStorage(initial: QueuedOperation[] = []): QueueStorage {
  let state = [...initial];
  return { load: () => [...state], save: (operations) => void (state = [...operations]) };
}

export function localStorageQueueStorage(storageKey: string): QueueStorage {
  return {
    load() {
      if (typeof localStorage === 'undefined') return [];
      try {
        const raw = localStorage.getItem(storageKey);
        const parsed: unknown = raw ? JSON.parse(raw) : [];
        return Array.isArray(parsed) ? (parsed as QueuedOperation[]) : [];
      } catch (error) {
        console.warn(
          '[offline-queue] corrupted storage, resetting',
          error instanceof Error ? error.name : 'unknown',
        );
        return [];
      }
    },
    save(operations) {
      if (typeof localStorage === 'undefined') return;
      localStorage.setItem(storageKey, JSON.stringify(operations));
    },
  };
}

export type FlushOutcome = { succeeded: number; failed: number; pending: number };
export type FlushHandler = (operation: QueuedOperation) => Promise<void>;
export type QueueOptions = {
  /** Retry later (keep in queue) when this returns true; otherwise the op is dropped and reported. */
  isRetryable: (error: unknown) => boolean;
  onPermanentFailure?: (operation: QueuedOperation, error: unknown) => void;
  onSuccess?: (operation: QueuedOperation) => void;
};

type Listener = (operations: QueuedOperation[]) => void;

export class OfflineQueue {
  private readonly listeners = new Set<Listener>();
  private flushing: Promise<FlushOutcome> | null = null;

  constructor(
    private readonly storage: QueueStorage,
    private readonly options: QueueOptions,
  ) {}

  list(kind?: string): QueuedOperation[] {
    const all = this.storage.load();
    return kind ? all.filter((operation) => operation.kind === kind) : all;
  }

  find(dedupeKey: string): QueuedOperation | undefined {
    return this.storage.load().find((operation) => operation.dedupeKey === dedupeKey);
  }

  enqueue<P>(input: {
    kind: string;
    dedupeKey: string;
    payload: P;
    idempotencyKey: string;
  }): QueuedOperation<P> {
    const operations = this.storage.load();
    const existing = operations.find((operation) => operation.dedupeKey === input.dedupeKey);
    const next: QueuedOperation<P> = {
      id: existing?.id ?? input.idempotencyKey,
      kind: input.kind,
      dedupeKey: input.dedupeKey,
      payload: input.payload,
      idempotencyKey: existing?.idempotencyKey ?? input.idempotencyKey,
      createdAt: existing?.createdAt ?? Date.now(),
      attempts: existing?.attempts ?? 0,
    };
    const rest = operations.filter((operation) => operation.dedupeKey !== input.dedupeKey);
    this.persist([...rest, next as QueuedOperation]);
    return next;
  }

  remove(id: string): void {
    this.persist(this.storage.load().filter((operation) => operation.id !== id));
  }

  subscribe(listener: Listener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  /** Processes operations in FIFO order; stops at the first retryable failure (keeps ordering). Single-flight. */
  flush(handler: FlushHandler): Promise<FlushOutcome> {
    if (!this.flushing) {
      this.flushing = this.runFlush(handler).finally(() => {
        this.flushing = null;
      });
    }
    return this.flushing;
  }

  private async runFlush(handler: FlushHandler): Promise<FlushOutcome> {
    const outcome: FlushOutcome = { succeeded: 0, failed: 0, pending: 0 };
    for (const operation of this.storage.load()) {
      const shouldContinue = await this.processOne(operation, handler, outcome);
      if (!shouldContinue) break;
    }
    outcome.pending = this.storage.load().length;
    return outcome;
  }

  private async processOne(
    operation: QueuedOperation,
    handler: FlushHandler,
    outcome: FlushOutcome,
  ): Promise<boolean> {
    const current = this.storage.load().find((candidate) => candidate.id === operation.id);
    if (!current) return true;
    try {
      await handler(current);
      this.removeIfUnchanged(current);
      outcome.succeeded += 1;
      this.options.onSuccess?.(current);
      return true;
    } catch (error) {
      if (this.options.isRetryable(error)) {
        this.bumpAttempts(current.id);
        return false;
      }
      this.remove(current.id);
      outcome.failed += 1;
      this.options.onPermanentFailure?.(current, error);
      return true;
    }
  }

  /** A newer payload may have been enqueued while the request was in flight — keep it. */
  private removeIfUnchanged(sent: QueuedOperation): void {
    const latest = this.storage.load().find((operation) => operation.id === sent.id);
    if (latest && JSON.stringify(latest.payload) !== JSON.stringify(sent.payload)) return;
    this.remove(sent.id);
  }

  private bumpAttempts(id: string): void {
    this.persist(
      this.storage
        .load()
        .map((operation) =>
          operation.id === id ? { ...operation, attempts: operation.attempts + 1 } : operation,
        ),
    );
  }

  private persist(operations: QueuedOperation[]): void {
    this.storage.save(operations);
    this.listeners.forEach((listener) => listener(operations));
  }
}
