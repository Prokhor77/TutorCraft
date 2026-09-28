import type { ReactNode } from 'react';
import { SiteFooter } from '@/components/landing/site-footer';
import { SiteHeader } from '@/components/landing/site-header';
import { MAIN_CONTENT_ID } from '@/components/layout/skip-link';
import { CenteredCard, PublicBlobs } from '@/components/public/status-card';

/** Checkout and invite pages: public header/footer around a centred 2rem Stitch card on a soft canvas. */
export default function CheckoutLayout({ children }: { children: ReactNode }) {
  return (
    <div className="relative flex min-h-dvh flex-col overflow-clip bg-background">
      <PublicBlobs />
      <SiteHeader />
      <main
        id={MAIN_CONTENT_ID}
        className="relative flex flex-1 items-start justify-center px-4 py-10 sm:items-center sm:py-16"
      >
        <CenteredCard>{children}</CenteredCard>
      </main>
      <SiteFooter />
    </div>
  );
}
