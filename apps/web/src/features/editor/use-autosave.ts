'use client';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useSaveStore } from '@/stores/save-store';

/** UX-03: drafts are saved at least every 10 s; we use 8 s to stay inside the budget with latency. */
export const AUTOSAVE_INTERVAL_MS = 8000;
const LOCAL_DRAFT_PREFIX = 'tc:draft:';

export type AutosaveStatus = 'idle' | 'dirty' | 'saving' | 'saved' | 'error' | 'invalid';

type Options<T> = {
  value: T;
  /** Returns the persisted value (or void). Throwing marks status `error` and retries next tick. */
  save: (value: T) => Promise<unknown>;
  enabled?: boolean;
  /** When false, the server save is postponed (e.g. image without alt) — local draft still protects data. */
  isValid?: (value: T) => boolean;
  /** Local draft key: protects against tab close / crash before the server save. */
  draftKey?: string;
  intervalMs?: number;
  onError?: (error: unknown) => void;
};

export function readLocalDraft<T>(draftKey: string): { value: T; savedAt: number } | null {
  try {
    const raw = localStorage.getItem(LOCAL_DRAFT_PREFIX + draftKey);
    return raw ? (JSON.parse(raw) as { value: T; savedAt: number }) : null;
  } catch {
    return null;
  }
}

export function clearLocalDraft(draftKey: string): void {
  try {
    localStorage.removeItem(LOCAL_DRAFT_PREFIX + draftKey);
  } catch (error) {
    console.warn(
      '[autosave] cannot clear local draft',
      error instanceof Error ? error.name : 'unknown',
    );
  }
}

function writeLocalDraft(draftKey: string, value: unknown): void {
  try {
    localStorage.setItem(
      LOCAL_DRAFT_PREFIX + draftKey,
      JSON.stringify({ value, savedAt: Date.now() }),
    );
  } catch (error) {
    console.warn(
      '[autosave] local draft not stored',
      error instanceof Error ? error.name : 'unknown',
    );
  }
}

export function useAutosave<T>({
  value,
  save,
  enabled = true,
  isValid,
  draftKey,
  intervalMs = AUTOSAVE_INTERVAL_MS,
  onError,
}: Options<T>) {
  const [status, setStatus] = useState<AutosaveStatus>('idle');
  const [lastSavedAt, setLastSavedAt] = useState<Date | null>(null);
  const latest = useRef(value);
  const savedSnapshot = useRef(JSON.stringify(value));
  const inFlight = useRef<Promise<void> | null>(null);
  const saveRef = useRef(save);
  saveRef.current = save;

  latest.current = value;
  const serialized = JSON.stringify(value);
  const dirty = serialized !== savedSnapshot.current;

  useEffect(() => {
    if (!enabled || !dirty) return;
    setStatus((current) => (current === 'saving' ? current : 'dirty'));
    if (draftKey) writeLocalDraft(draftKey, value);
  }, [serialized, dirty, enabled, draftKey, value]);

  const flush = useCallback(async (): Promise<void> => {
    if (inFlight.current) await inFlight.current;
    const snapshot = JSON.stringify(latest.current);
    if (!enabled || snapshot === savedSnapshot.current) return;
    if (isValid && !isValid(latest.current)) {
      setStatus('invalid');
      return;
    }
    setStatus('saving');
    const run = saveRef
      .current(latest.current)
      .then(() => {
        savedSnapshot.current = snapshot;
        setLastSavedAt(new Date());
        useSaveStore.getState().markSaved();
        setStatus(JSON.stringify(latest.current) === snapshot ? 'saved' : 'dirty');
        if (draftKey && JSON.stringify(latest.current) === snapshot) clearLocalDraft(draftKey);
      })
      .catch((error: unknown) => {
        setStatus('error');
        onError?.(error);
      })
      .finally(() => {
        inFlight.current = null;
      });
    inFlight.current = run;
    await run;
  }, [enabled, isValid, draftKey, onError]);

  useEffect(() => {
    if (!enabled) return;
    const timer = setInterval(() => void flush(), intervalMs);
    const onHidden = () => {
      if (document.visibilityState === 'hidden') void flush();
    };
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      if (JSON.stringify(latest.current) === savedSnapshot.current) return;
      void flush();
      event.preventDefault();
    };
    document.addEventListener('visibilitychange', onHidden);
    window.addEventListener('beforeunload', onBeforeUnload);
    return () => {
      clearInterval(timer);
      document.removeEventListener('visibilitychange', onHidden);
      window.removeEventListener('beforeunload', onBeforeUnload);
    };
  }, [enabled, flush, intervalMs]);

  /** Call after the server-side value is (re)loaded so it is not considered dirty. */
  const markSaved = useCallback((persisted: T) => {
    savedSnapshot.current = JSON.stringify(persisted);
    setStatus('idle');
  }, []);

  return { status, lastSavedAt, flush, markSaved, dirty };
}
