import type { ReactNode } from 'react';
import { AuthenticatedApp } from './authenticated-app';

export default function AppLayout({ children }: { children: ReactNode }) {
  return <AuthenticatedApp>{children}</AuthenticatedApp>;
}
