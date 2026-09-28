'use client';
import { usePathname } from 'next/navigation';
import { useEffect } from 'react';
import { startActivityReporting, trackPageView } from '@/lib/activity/reporter';

/** Feeds the admin activity log: page views of the signed-in app plus browser errors (see lib/activity/reporter). */
export function ActivityTracker() {
  const pathname = usePathname();
  useEffect(() => startActivityReporting(), []);
  useEffect(() => {
    if (pathname) trackPageView(pathname);
  }, [pathname]);
  return null;
}
