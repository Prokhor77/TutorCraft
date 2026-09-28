import type { ReactNode } from 'react';
import { cn } from '@/lib/utils/cn';

/**
 * Stitch page headline: optional uppercase eyebrow («АНАЛИТИКА КУРСА»), headline-lg (24/32 on mobile),
 * status chips next to the title (`meta`), description and right-aligned actions.
 */
export function PageHeader({
  title,
  eyebrow,
  meta,
  description,
  actions,
  className,
}: {
  title: ReactNode;
  eyebrow?: ReactNode;
  meta?: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  className?: string;
}) {
  return (
    <header
      className={cn(
        'mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between',
        className,
      )}
    >
      <div className="flex min-w-0 flex-col gap-1.5">
        {eyebrow ? (
          <span className="text-label-md uppercase text-text-muted">{eyebrow}</span>
        ) : null}
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5">
          <h1 className="text-2xl md:text-3xl">{title}</h1>
          {meta}
        </div>
        {description ? <p className="max-w-3xl text-sm text-text-muted">{description}</p> : null}
      </div>
      {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
    </header>
  );
}

export function Section({
  title,
  actions,
  children,
  className,
}: {
  title: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={cn('flex flex-col gap-3', className)}>
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-lg">{title}</h2>
        {actions}
      </div>
      {children}
    </section>
  );
}
