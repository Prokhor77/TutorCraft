import type { ReactNode } from 'react';
import { ActivityTracker } from '@/features/activity/activity-tracker';
import { AuthenticatedApp } from './authenticated-app';

export default function AppLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <ActivityTracker />
      <AuthenticatedApp>{children}</AuthenticatedApp>
    </>
  );
}
