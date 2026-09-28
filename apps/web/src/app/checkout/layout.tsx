import type { ReactNode } from 'react';
import { SiteFooter } from '@/components/landing/site-footer';
import { SiteHeader } from '@/components/landing/site-header';
import { MAIN_CONTENT_ID } from '@/components/layout/skip-link';

/** Checkout and invite pages: public header/footer around a centred Stitch card. */
export default function CheckoutLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col bg-background">
      <SiteHeader />
      <main
        id={MAIN_CONTENT_ID}
        className="flex flex-1 items-start justify-center px-4 py-10 sm:items-center sm:py-16"
      >
        <div className="w-full max-w-md rounded-lg border border-card-border bg-surface p-6 shadow-md sm:p-8">
          {children}
        </div>
      </main>
      <SiteFooter />
    </div>
  );
}
