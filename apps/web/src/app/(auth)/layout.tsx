import type { ReactNode } from 'react';
import { Brand } from '@/components/layout/brand';
import { LanguageMenu, ThemeMenu } from '@/components/layout/preferences-menu';
import { MAIN_CONTENT_ID } from '@/components/layout/skip-link';

export default function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col bg-background">
      <header className="flex h-header items-center justify-between px-page-x">
        <Brand />
        <div className="flex items-center gap-1">
          <LanguageMenu />
          <ThemeMenu />
        </div>
      </header>
      <main
        id={MAIN_CONTENT_ID}
        className="flex flex-1 items-start justify-center px-4 py-8 sm:items-center"
      >
        <div className="w-full max-w-md rounded-xl border border-border bg-surface p-6 shadow-md sm:p-8">
          {children}
        </div>
      </main>
    </div>
  );
}
