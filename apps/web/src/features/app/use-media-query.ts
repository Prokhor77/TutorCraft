'use client';
import { useSyncExternalStore } from 'react';

/** Layout breakpoints (Stitch): desktop ≥ 1280, tablet 768–1279, mobile < 768 — mirror Tailwind `xl`/`md`. */
export const BREAKPOINTS = {
  desktop: '(min-width: 1280px)',
  tablet: '(min-width: 768px)',
} as const;

export function useMediaQuery(query: string, serverValue = false): boolean {
  return useSyncExternalStore(
    (callback) => {
      const list = window.matchMedia(query);
      list.addEventListener('change', callback);
      return () => list.removeEventListener('change', callback);
    },
    () => window.matchMedia(query).matches,
    () => serverValue,
  );
}
