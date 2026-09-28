import { ChevronRight } from 'lucide-react';
import Link from 'next/link';
import type { ReactNode } from 'react';
import { cn } from '@/lib/utils/cn';

export type Crumb = { label: string; href?: string };

/** Stitch breadcrumb trail: muted links separated by chevrons, current page as a tinted pill. */
export function Breadcrumbs({
  items,
  label,
  className,
}: {
  items: Crumb[];
  label: string;
  className?: string;
}) {
  return (
    <nav aria-label={label} className={cn('min-w-0', className)}>
      <ol className="flex flex-wrap items-center gap-1 text-xs text-text-muted">
        {items.map((crumb, index) => {
          const last = index === items.length - 1;
          return (
            <li key={`${crumb.label}-${index}`} className="flex min-w-0 items-center gap-1">
              {index > 0 ? (
                <ChevronRight className="size-3.5 shrink-0 text-outline-variant" aria-hidden />
              ) : null}
              {last ? (
                <span
                  aria-current="page"
                  className="max-w-64 truncate rounded-full bg-surface-muted px-2 py-0.5 text-label-md text-text"
                >
                  {crumb.label}
                </span>
              ) : crumb.href ? (
                <Link
                  href={crumb.href}
                  className="max-w-56 truncate rounded-sm transition-colors hover:text-primary"
                >
                  {crumb.label}
                </Link>
              ) : (
                <span className="max-w-56 truncate">{crumb.label}</span>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}

/**
 * Stitch page header: a white 2rem card with optional breadcrumbs, uppercase eyebrow, headline-lg title with status
 * chips (`meta`), description, right-aligned actions and a bottom toolbar row (`children`: pill tabs, filters, sync
 * hints). `plain` drops the card chrome for headers that sit inside another panel.
 */
export function PageHeader({
  title,
  eyebrow,
  meta,
  description,
  actions,
  breadcrumbs,
  children,
  plain,
  className,
}: {
  title: ReactNode;
  eyebrow?: ReactNode;
  meta?: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  breadcrumbs?: ReactNode;
  children?: ReactNode;
  plain?: boolean;
  className?: string;
}) {
  return (
    <header
      className={cn(
        'mb-6 flex flex-col gap-4',
        !plain && 'rounded-lg border border-card-border bg-surface p-5 shadow-sm sm:p-6',
        className,
      )}
    >
      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex min-w-0 flex-col gap-1.5">
          {breadcrumbs}
          {eyebrow ? (
            <span className="text-label-md uppercase text-text-muted">{eyebrow}</span>
          ) : null}
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5">
            <h1 className="text-2xl md:text-3xl">{title}</h1>
            {meta}
          </div>
          {description ? <p className="max-w-3xl text-sm text-text-muted">{description}</p> : null}
        </div>
        {actions ? (
          <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div>
        ) : null}
      </div>
      {children ? (
        <div className="flex flex-wrap items-center justify-between gap-3">{children}</div>
      ) : null}
    </header>
  );
}

/** Content panel with a headline-sm title row (Stitch «Статистика по теме», «Заметки репетитора»). */
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

/** White 2rem panel used for page sections (Stitch `bg-surface-container-lowest rounded-lg p-space-lg`). */
export function Panel({
  title,
  description,
  actions,
  children,
  className,
  as: Tag = 'section',
}: {
  title?: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
  as?: 'section' | 'div' | 'aside';
}) {
  return (
    <Tag
      className={cn(
        'flex min-w-0 flex-col gap-4 rounded-lg border border-card-border bg-surface p-5 shadow-sm sm:p-6',
        className,
      )}
    >
      {title || actions ? (
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div className="flex min-w-0 flex-col gap-0.5">
            {title ? <h2 className="text-lg">{title}</h2> : null}
            {description ? <p className="text-xs text-text-muted">{description}</p> : null}
          </div>
          {actions}
        </div>
      ) : null}
      {children}
    </Tag>
  );
}
