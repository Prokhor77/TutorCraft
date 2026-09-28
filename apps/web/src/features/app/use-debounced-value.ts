'use client';
import { useEffect, useState } from 'react';

export const DEFAULT_DEBOUNCE_MS = 300;

/** Value that settles `delayMs` after the last change (search fields: one request per pause, not per keystroke). */
export function useDebouncedValue<T>(value: T, delayMs: number = DEFAULT_DEBOUNCE_MS): T {
  const [settled, setSettled] = useState(value);
  useEffect(() => {
    const timer = window.setTimeout(() => setSettled(value), delayMs);
    return () => window.clearTimeout(timer);
  }, [value, delayMs]);
  return settled;
}
