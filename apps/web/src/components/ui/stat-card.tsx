import type { LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import { cn } from '@/lib/utils/cn';

export type StatTone = 'primary' | 'success' | 'warning' | 'danger';

const ICON_TONES: Record<StatTone, string> = {
  primary: 'bg-primary-soft text-primary',
  success: 'bg-success-soft text-success',
  warning: 'bg-warning-soft text-warning',
  danger: 'bg-danger-soft text-danger',
};

/**
 * Stitch stat card: uppercase label, tinted icon chip, headline value with a muted unit and an optional footer
 * (progress bar, chip, hint). Only for metrics the API actually returns.
 */
export function StatCard({
  label,
  value,
  unit,
  icon: Icon,
  tone = 'primary',
  footer,
  className,
}: {
  label: string;
  value: ReactNode;
  unit?: ReactNode;
  icon: LucideIcon;
  tone?: StatTone;
  footer?: ReactNode;
  className?: string;
}) {
  return (
    <section
      aria-label={label}
      className={cn(
        'flex min-w-0 flex-col gap-3 rounded-lg border border-card-border bg-surface p-5 shadow-sm transition-shadow duration-fast hover:shadow-md sm:p-6',
        className,
      )}
    >
      {/* Phones: icon above the label so long uppercase labels never push it out; sm+: label left, icon right. */}
      <div className="flex flex-col items-start gap-2 sm:flex-row-reverse sm:justify-between">
        <span
          className={cn(
            'flex size-9 shrink-0 items-center justify-center rounded-full',
            ICON_TONES[tone],
          )}
        >
          <Icon className="size-[1.125rem]" aria-hidden />
        </span>
        <h2 className="min-w-0 font-sans text-label-md uppercase text-text-muted">{label}</h2>
      </div>
      <p className="flex min-w-0 items-baseline gap-1.5">
        <span className="truncate font-heading text-3xl font-bold tracking-tight sm:text-4xl">
          {value}
        </span>
        {unit ? <span className="truncate text-sm text-text-muted">{unit}</span> : null}
      </p>
      {footer ? <div className="text-xs text-text-muted">{footer}</div> : null}
    </section>
  );
}

/** Grid for 2–4 stat cards: 2 columns on phones (Stitch mobile), 4 on desktop. */
export function StatGrid({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div className={cn('grid grid-cols-2 gap-3 md:gap-gutter lg:grid-cols-4', className)}>
      {children}
    </div>
  );
}
