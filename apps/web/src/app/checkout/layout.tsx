import type { ReactNode } from 'react';
import { Brand } from '@/components/layout/brand';

export default function CheckoutLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col bg-background">
      <header className="flex h-header items-center px-page-x">
        <Brand />
      </header>
      <main
        id="main-content"
        className="flex flex-1 items-start justify-center px-4 py-8 sm:items-center"
      >
        <div className="w-full max-w-md rounded-lg border border-card-border bg-surface p-6 shadow-md">
          {children}
        </div>
      </main>
    </div>
  );
}
