import type { Metadata } from 'next';
import type { ReactNode } from 'react';
import { ActivityTracker } from '@/features/activity/activity-tracker';
import { robots } from '@/lib/seo/metadata';
import { AuthenticatedApp } from './authenticated-app';

/** The authenticated app renders a spinner for crawlers (ADR-003): never index it, never follow its links. */
export function generateMetadata(): Metadata {
  return { robots: robots('private') };
}

export default function AppLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <ActivityTracker />
      <AuthenticatedApp>{children}</AuthenticatedApp>
    </>
  );
}
