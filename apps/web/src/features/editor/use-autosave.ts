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
  /** Also save this long after the last change (quick feedback for forms); off by default. */
  debounceMs?: number;
  /** Reported once per failure streak (retries stay silent until a save succeeds again). */
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
  debounceMs,
  onError,
}: Options<T>) {
  const [status, setStatus] = useState<AutosaveStatus>('idle');
  const [lastSavedAt, setLastSavedAt] = useState<Date | null>(null);
  const latest = useRef(value);
  const savedSnapshot = useRef(JSON.stringify(value));
  const inFlight = useRef<Promise<void> | null>(null);
  const failing = useRef(false);
  // Callbacks live in refs so `flush` stays stable: inline props must not restart the timers on every render.
  const config = useRef({ save, isValid, onError, enabled, draftKey });
  config.current = { save, isValid, onError, enabled, draftKey };

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
    const { save: persist, isValid: valid, enabled: on, draftKey: key } = config.current;
    const snapshot = JSON.stringify(latest.current);
    if (!on || snapshot === savedSnapshot.current) return;
    if (valid && !valid(latest.current)) {
      setStatus('invalid');
      return;
    }
    setStatus('saving');
    const run = persist(latest.current)
      .then(() => {
        savedSnapshot.current = snapshot;
        failing.current = false;
        setLastSavedAt(new Date());
        useSaveStore.getState().markSaved();
        const upToDate = JSON.stringify(latest.current) === snapshot;
        setStatus(upToDate ? 'saved' : 'dirty');
        if (key && upToDate) clearLocalDraft(key);
      })
      .catch((error: unknown) => {
        setStatus('error');
        if (!failing.current) config.current.onError?.(error);
        failing.current = true;
      })
      .finally(() => {
        inFlight.current = null;
      });
    inFlight.current = run;
    await run;
  }, []);

  useEffect(() => {
    if (!enabled || !dirty || !debounceMs) return;
    const timer = setTimeout(() => void flush(), debounceMs);
    return () => clearTimeout(timer);
  }, [serialized, dirty, enabled, debounceMs, flush]);

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

  // In-app navigation (another question, another page) unmounts the editor: persist what is pending.
  useEffect(() => () => void flush(), [flush]);

  /** Call after the server-side value is (re)loaded so it is not considered dirty. */
  const markSaved = useCallback((persisted: T) => {
    savedSnapshot.current = JSON.stringify(persisted);
    setStatus('idle');
  }, []);

  return { status, lastSavedAt, flush, markSaved, dirty };
}
