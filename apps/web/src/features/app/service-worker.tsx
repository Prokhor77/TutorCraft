'use client';
import { useEffect } from 'react';

const SW_PATH = '/sw.js';

/** PWA (NFR-COMP-02): registers the offline cache service worker in production builds only. */
export function ServiceWorkerRegistrar() {
  useEffect(() => {
    if (process.env.NODE_ENV !== 'production' || !('serviceWorker' in navigator)) return;
    navigator.serviceWorker.register(SW_PATH, { scope: '/' }).catch((error: unknown) => {
      console.warn('[sw] registration failed', error instanceof Error ? error.message : 'unknown');
    });
  }, []);
  return null;
}
